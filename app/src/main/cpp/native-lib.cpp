// =============================================================================
// FapHousePro — native security engine
// =============================================================================

#include <jni.h>
#include <android/log.h>
#include <android/api-level.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <ctime>
#include <string>
#include <vector>
#include <array>
#include <sstream>

#define TAG "FHP-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static JavaVM* g_vm = nullptr;
static jobject g_bridge_instance = nullptr;
static jobject g_app_context = nullptr;
static jclass  g_bridge_class = nullptr;
static jmethodID g_get_apk_sig = nullptr;
static jmethodID g_get_version_name = nullptr;
static jmethodID g_get_version_code = nullptr;
static jmethodID g_get_device_model = nullptr;
static jmethodID g_get_package_name = nullptr;
static jmethodID g_get_installer = nullptr;
static jmethodID g_is_debugger_attached = nullptr;

static std::array<uint8_t, 32> g_widevine_id{};
static bool g_widevine_set = false;
static std::string g_cached_device_id;

// =============================================================================
// 1. OBFUSCATED KEYS (XOR 0x5A)
// =============================================================================
static const char* AES_HEX_OBF =
    "\x62\x3C\x63\x3B\x68\x38\x69\x39\x6E\x3E\x6F\x3F\x6C\x3C\x6D\x3B"
    "\x62\x38\x63\x39\x6A\x3E\x6B\x3F\x68\x3C\x69\x3B\x6E\x38\x6F\x39"
    "\x6C\x3E\x6D\x3F\x62\x3C\x63\x3B\x6A\x38\x6B\x39\x68\x3E\x69\x3F"
    "\x6E\x3C\x6F\x3B\x6C\x38\x6D\x39\x62\x3E\x63\x3F\x6A\x3C\x6B\x3B";

static const char* HMAC_HEX_OBF =
    "\x6B\x3B\x68\x38\x69\x39\x6E\x3E\x6F\x3F\x6C\x3C\x6D\x3B\x62\x38"
    "\x63\x39\x6A\x3E\x6B\x3F\x68\x3C\x69\x3B\x6E\x38\x6F\x39\x6C\x3E"
    "\x6D\x3F\x62\x3C\x63\x3B\x6A\x38\x6B\x39\x68\x3E\x69\x3F\x6E\x3C"
    "\x6F\x3B\x6C\x38\x6D\x39\x62\x3E\x63\x3F\x6A\x3C\x6B\x3B\x68\x38";

static volatile uint8_t g_key_sink = 0;

static std::array<uint8_t, 32> deobfuscate_hex(const char* obf) {
    std::array<uint8_t, 32> out{};
    size_t n = 0;
    char hexbuf[65] = {0};
    for (size_t i = 0; i < 64 && obf[i] != '\0'; ++i) {
        hexbuf[i] = static_cast<char>(obf[i] ^ 0x5A);
        g_key_sink = static_cast<uint8_t>(g_key_sink ^ hexbuf[i]);
    }
    for (size_t i = 0; i + 1 < 64; i += 2) {
        auto nib = [](char c) -> int {
            if (c >= '0' && c <= '9') return c - '0';
            if (c >= 'a' && c <= 'f') return c - 'a' + 10;
            if (c >= 'A' && c <= 'F') return c - 'A' + 10;
            return 0;
        };
        out[n++] = static_cast<uint8_t>((nib(hexbuf[i]) << 4) | nib(hexbuf[i + 1]));
        if (n == 32) break;
    }
    return out;
}

// =============================================================================
// 2. SHA-256
// =============================================================================
namespace sha256_impl {
    static constexpr uint32_t K[64] = {
        0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
    };
    static inline uint32_t ror(uint32_t x, int n){ return (x>>n)|(x<<(32-n)); }
    static void block(const uint8_t* p, uint32_t* H){
        uint32_t w[64];
        for (int i=0;i<16;i++) w[i]=(uint32_t(p[i*4])<<24)|(uint32_t(p[i*4+1])<<16)|(uint32_t(p[i*4+2])<<8)|p[i*4+3];
        for (int i=16;i<64;i++){
            uint32_t s0=ror(w[i-15],7)^ror(w[i-15],18)^(w[i-15]>>3);
            uint32_t s1=ror(w[i-2],17)^ror(w[i-2],19)^(w[i-2]>>10);
            w[i]=w[i-16]+s0+w[i-7]+s1;
        }
        uint32_t a=H[0],b=H[1],c=H[2],d=H[3],e=H[4],f=H[5],g=H[6],h=H[7];
        for (int i=0;i<64;i++){
            uint32_t S1=ror(e,6)^ror(e,11)^ror(e,25);
            uint32_t ch=(e&f)^((~e)&g);
            uint32_t t1=h+S1+ch+K[i]+w[i];
            uint32_t S0=ror(a,2)^ror(a,13)^ror(a,22);
            uint32_t mj=(a&b)^(a&c)^(b&c);
            uint32_t t2=S0+mj;
            h=g;g=f;f=e;e=d+t1;d=c;c=b;b=a;a=t1+t2;
        }
        H[0]+=a;H[1]+=b;H[2]+=c;H[3]+=d;H[4]+=e;H[5]+=f;H[6]+=g;H[7]+=h;
    }
    static std::array<uint8_t,32> hash(const uint8_t* data, size_t len){
        uint32_t H[8]={0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19};
        size_t full = len/64;
        for (size_t i=0;i<full;i++) block(data+i*64, H);
        uint8_t tail[128]={0};
        size_t rem = len - full*64;
        if (rem) memcpy(tail, data+full*64, rem);
        tail[rem]=0x80;
        size_t tail_len = (rem<56)?64:128;
        uint64_t bits = (uint64_t)len*8;
        for (int i=0;i<8;i++) tail[tail_len-1-i]=uint8_t((bits>>(i*8))&0xFF);
        block(tail,H);
        if (tail_len==128) block(tail+64,H);
        std::array<uint8_t,32> out{};
        for (int i=0;i<8;i++){ out[i*4]=uint8_t(H[i]>>24); out[i*4+1]=uint8_t(H[i]>>16); out[i*4+2]=uint8_t(H[i]>>8); out[i*4+3]=uint8_t(H[i]); }
        return out;
    }
}

static std::array<uint8_t,32> sha256_buf(const uint8_t* d, size_t n){ return sha256_impl::hash(d,n); }
static std::array<uint8_t,32> sha256_str(const std::string& s){ return sha256_impl::hash(reinterpret_cast<const uint8_t*>(s.data()), s.size()); }

static std::string hex_encode(const uint8_t* d, size_t n){
    static const char* H="0123456789abcdef";
    std::string out; out.resize(n*2);
    for (size_t i=0;i<n;i++){ out[i*2]=H[d[i]>>4]; out[i*2+1]=H[d[i]&0xF]; }
    return out;
}

// =============================================================================
// 3. HMAC-SHA256
// =============================================================================
static std::array<uint8_t,32> hmac_sha256(const std::array<uint8_t,32>& key,
                                          const uint8_t* msg, size_t msg_len) {
    std::array<uint8_t,64> ipad{}, opad{};
    for (int i=0;i<32;i++){ ipad[i]=key[i]^0x36; opad[i]=key[i]^0x5c; }
    for (int i=32;i<64;i++){ ipad[i]=0x36; opad[i]=0x5c; }
    std::vector<uint8_t> inner;
    inner.reserve(64+msg_len);
    inner.insert(inner.end(), ipad.begin(), ipad.end());
    inner.insert(inner.end(), msg, msg+msg_len);
    auto inner_hash = sha256_impl::hash(inner.data(), inner.size());
    std::array<uint8_t,96> outer{};
    for (int i=0;i<64;i++) outer[i]=opad[i];
    for (int i=0;i<32;i++) outer[64+i]=inner_hash[i];
    return sha256_impl::hash(outer.data(), outer.size());
}
static std::string hmac_sha256_hex(const std::array<uint8_t,32>& key, const std::string& msg){
    auto d = hmac_sha256(key, reinterpret_cast<const uint8_t*>(msg.data()), msg.size());
    return hex_encode(d.data(), d.size());
}

// =============================================================================
// 4. AES-256-GCM
// =============================================================================
namespace aes_impl {
    static const uint8_t sbox[256] = {
        0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
        0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
        0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
        0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
        0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
        0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
        0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
        0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
        0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
        0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
        0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
        0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
        0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
        0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
        0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
        0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16
    };
    static const uint8_t rcon[11] = {0x00,0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36};

    struct AesCtx { uint8_t rk[240]; };

    static inline uint8_t mul(uint8_t a, uint8_t b){
        uint8_t p=0;
        for (int i=0;i<8;i++){
            if (b&1) p^=a;
            uint8_t hi = a&0x80;
            a <<= 1;
            if (hi) a ^= 0x1b;
            b >>= 1;
        }
        return p;
    }

    static void expand_key(AesCtx& c, const std::array<uint8_t,32>& key){
        memcpy(c.rk, key.data(), 32);
        int bytes_gen=32, rcon_iter=1;
        uint8_t t[4];
        while (bytes_gen < 240){
            memcpy(t, c.rk+bytes_gen-4, 4);
            if (bytes_gen % 32 == 0){
                uint8_t tmp=t[0]; t[0]=t[1]; t[1]=t[2]; t[2]=t[3]; t[3]=tmp;
                for (int i=0;i<4;i++) t[i]=sbox[t[i]];
                t[0]^=rcon[rcon_iter++];
            } else if (bytes_gen % 32 == 16){
                for (int i=0;i<4;i++) t[i]=sbox[t[i]];
            }
            for (int i=0;i<4;i++){ c.rk[bytes_gen]=c.rk[bytes_gen-32]^t[i]; bytes_gen++; }
        }
    }

    static void encrypt_block(const AesCtx& c, const uint8_t in[16], uint8_t out[16]){
        uint8_t s[16]; memcpy(s,in,16);
        for (int i=0;i<16;i++) s[i]^=c.rk[i];
        for (int round=1; round<=14; round++){
            uint8_t t[16];
            t[0]=sbox[s[0]];  t[1]=sbox[s[5]];  t[2]=sbox[s[10]]; t[3]=sbox[s[15]];
            t[4]=sbox[s[4]];  t[5]=sbox[s[9]];  t[6]=sbox[s[14]]; t[7]=sbox[s[3]];
            t[8]=sbox[s[8]];  t[9]=sbox[s[13]]; t[10]=sbox[s[2]]; t[11]=sbox[s[7]];
            t[12]=sbox[s[12]];t[13]=sbox[s[1]]; t[14]=sbox[s[6]]; t[15]=sbox[s[11]];
            if (round != 14){
                for (int col=0; col<4; col++){
                    uint8_t* p = t + col*4;
                    uint8_t a0=p[0],a1=p[1],a2=p[2],a3=p[3];
                    p[0] = mul(a0,2)^mul(a1,3)^a2^a3;
                    p[1] = a0^mul(a1,2)^mul(a2,3)^a3;
                    p[2] = a0^a1^mul(a2,2)^mul(a3,3);
                    p[3] = mul(a0,3)^a1^a2^mul(a3,2);
                }
            }
            for (int i=0;i<16;i++) s[i]=t[i]^c.rk[round*16+i];
        }
        memcpy(out,s,16);
    }

    static void ghash_mult(uint8_t X[16], const uint8_t H[16]){
        uint8_t Z[16]={0}, V[16];
        memcpy(V,H,16);
        for (int i=0;i<128;i++){
            if ((X[i/8]>>(7-(i%8)))&1){
                for (int j=0;j<16;j++) Z[j]^=V[j];
            }
            int lsb = V[15]&1;
            uint8_t carry=0;
            for (int j=0;j<16;j++){
                uint8_t nxt_carry = V[j]>>7;
                V[j] = uint8_t((V[j]<<1) | carry);
                carry = nxt_carry;
            }
            if (lsb) V[0]^=0xE1;
        }
        memcpy(X,Z,16);
    }

    static void ghash(const uint8_t H[16], const uint8_t* aad, size_t aad_len,
                     const uint8_t* ct, size_t ct_len, uint8_t out[16]){
        uint8_t Y[16]={0}, blk[16];
        for (size_t i=0;i<aad_len;i+=16){
            memset(blk,0,16);
            size_t n = (i+16<=aad_len)?16:(aad_len-i);
            memcpy(blk,aad+i,n);
            for (int j=0;j<16;j++) Y[j]^=blk[j];
            ghash_mult(Y,H);
        }
        for (size_t i=0;i<ct_len;i+=16){
            memset(blk,0,16);
            size_t n = (i+16<=ct_len)?16:(ct_len-i);
            memcpy(blk,ct+i,n);
            for (int j=0;j<16;j++) Y[j]^=blk[j];
            ghash_mult(Y,H);
        }
        uint64_t al=(uint64_t)aad_len*8, cl=(uint64_t)ct_len*8;
        uint8_t lb[16];
        for (int i=0;i<8;i++) lb[i]=uint8_t((al>>(56-i*8))&0xFF);
        for (int i=0;i<8;i++) lb[8+i]=uint8_t((cl>>(56-i*8))&0xFF);
        for (int j=0;j<16;j++) Y[j]^=lb[j];
        ghash_mult(Y,H);
        memcpy(out,Y,16);
    }

    static void gctr(const AesCtx& c, const uint8_t ICB[16],
                    const uint8_t* in, size_t len, uint8_t* out){
        uint8_t cb[16]; memcpy(cb,ICB,16);
        size_t i=0;
        while (i<len){
            uint8_t ks[16];
            encrypt_block(c, cb, ks);
            size_t blk=(len-i<16)?(len-i):16;
            for (size_t j=0;j<blk;j++) out[i+j]=in[i+j]^ks[j];
            for (int j=15;j>=12;j--){ if (++cb[j]) break; }
            i+=blk;
        }
    }

    static bool gcm_encrypt(const std::array<uint8_t,32>& key,
                           const uint8_t nonce[12],
                           const uint8_t* aad, size_t aad_len,
                           const uint8_t* pt, size_t pt_len,
                           uint8_t* ct_out, uint8_t tag_out[16]){
        AesCtx c; expand_key(c, key);
        uint8_t zero[16]={0}, H[16];
        encrypt_block(c, zero, H);
        uint8_t J0[16];
        memcpy(J0, nonce, 12);
        J0[12]=0; J0[13]=0; J0[14]=0; J0[15]=1;
        gctr(c, J0, pt, pt_len, ct_out);
        uint8_t S[16], ekJ0[16];
        ghash(H, aad, aad_len, ct_out, pt_len, S);
        encrypt_block(c, J0, ekJ0);
        for (int i=0;i<16;i++) tag_out[i]=S[i]^ekJ0[i];
        return true;
    }

    static bool gcm_decrypt(const std::array<uint8_t,32>& key,
                           const uint8_t nonce[12],
                           const uint8_t* aad, size_t aad_len,
                           const uint8_t* ct, size_t ct_len,
                           const uint8_t tag[16], uint8_t* pt_out){
        AesCtx c; expand_key(c, key);
        uint8_t zero[16]={0}, H[16];
        encrypt_block(c, zero, H);
        uint8_t J0[16];
        memcpy(J0, nonce, 12);
        J0[12]=0; J0[13]=0; J0[14]=0; J0[15]=1;
        uint8_t S[16], ekJ0[16], tag_calc[16];
        ghash(H, aad, aad_len, ct, ct_len, S);
        encrypt_block(c, J0, ekJ0);
        for (int i=0;i<16;i++) tag_calc[i]=S[i]^ekJ0[i];
        int diff=0;
        for (int i=0;i<16;i++) diff |= tag[i]^tag_calc[i];
        if (diff != 0) return false;
        gctr(c, J0, ct, ct_len, pt_out);
        return true;
    }
}

// =============================================================================
// 5. BASE64
// =============================================================================
static std::string b64_encode(const uint8_t* d, size_t n){
    static const char* T="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string out; out.reserve((n+2)/3*4);
    for (size_t i=0;i<n;i+=3){
        uint32_t v = (uint32_t)d[i]<<16;
        if (i+1<n) v |= (uint32_t)d[i+1]<<8;
        if (i+2<n) v |= d[i+2];
        out.push_back(T[(v>>18)&0x3F]);
        out.push_back(T[(v>>12)&0x3F]);
        out.push_back(i+1<n ? T[(v>>6)&0x3F] : '=');
        out.push_back(i+2<n ? T[v&0x3F] : '=');
    }
    return out;
}
static std::string b64url_encode(const uint8_t* d, size_t n){
    std::string s = b64_encode(d,n);
    for (auto& c : s){ if (c=='+')c='-'; else if(c=='/')c='_'; }
    return s;
}
static std::vector<uint8_t> b64_decode(const std::string& s){
    static int T[256];
    static bool init=false;
    if (!init){ for (int i=0;i<256;i++) T[i]=-1;
        const char* tbl="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        for (int i=0;i<64;i++) T[(uint8_t)tbl[i]]=i;
        init=true;
    }
    std::vector<uint8_t> out; out.reserve(s.size());
    uint32_t buf=0; int bits=0;
    for (char c : s){
        if (c=='=' || c=='-' || c=='_') {
            if (c=='-') c='+'; else if(c=='_') c='/'; else break;
        }
        int v = T[(uint8_t)c];
        if (v<0) continue;
        buf=(buf<<6)|v; bits+=6;
        if (bits>=8){ bits-=8; out.push_back(uint8_t((buf>>bits)&0xFF)); }
    }
    return out;
}

// =============================================================================
// 6. UTILS
// =============================================================================
static std::string read_file_limited(const char* path, size_t max){
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return {};
    std::string out; out.reserve(4096);
    char buf[4096];
    size_t total=0;
    ssize_t n;
    while ((n = read(fd, buf, sizeof(buf))) > 0){
        total += (size_t)n;
        if (total > max){ close(fd); return {}; }
        out.append(buf, (size_t)n);
    }
    close(fd);
    return out;
}
static bool file_exists(const char* path){
    struct stat st{};
    return access(path, F_OK) == 0 || stat(path, &st) == 0;
}
static bool contains_ci(const std::string& hay, const char* needle){
    size_t hn = strlen(needle);
    if (hn == 0 || hay.size() < hn) return false;
    for (size_t i=0;i+hn<=hay.size();i++){
        bool m=true;
        for (size_t j=0;j<hn;j++){
            char a=hay[i+j], b=needle[j];
            if (a>='A'&&a<='Z') a=char(a-'A'+'a');
            if (b>='A'&&b<='Z') b=char(b-'A'+'a');
            if (a!=b){ m=false; break; }
        }
        if (m) return true;
    }
    return false;
}

// =============================================================================
// 7. ANTI-TAMPER
// =============================================================================
static constexpr int ENV_FLAG_FRIDA       = 1 << 0;
static constexpr int ENV_FLAG_XPOSED      = 1 << 1;
static constexpr int ENV_FLAG_MAGISK      = 1 << 2;
static constexpr int ENV_FLAG_SU_BINARY   = 1 << 3;
static constexpr int ENV_FLAG_DEBUGGER    = 1 << 4;
static constexpr int ENV_FLAG_VPHONEGAGA  = 1 << 5;
static constexpr int ENV_FLAG_DUALAPP     = 1 << 6;
static constexpr int ENV_FLAG_EMULATOR    = 1 << 7;
static constexpr int ENV_FLAG_MAPS_SUS    = 1 << 8;

static int detect_frida(){
    std::string maps = read_file_limited("/proc/self/maps", 4*1024*1024);
    if (!maps.empty()){
        if (contains_ci(maps, "frida") || contains_ci(maps, "gum-js-loop") ||
            contains_ci(maps, "gmain") || contains_ci(maps, "linjector") ||
            contains_ci(maps, "frida-agent") || contains_ci(maps, "re.frida.server")) {
            return 1;
        }
    }
    const char* paths[] = {
        "/data/local/tmp/frida-server", "/data/local/tmp/re.frida.server",
        "/sdcard/frida-server",
    };
    for (auto p : paths) if (file_exists(p)) return 1;
    return 0;
}

static int detect_xposed(){
    if (file_exists("/system/framework/XposedBridge.jar")) return 1;
    if (file_exists("/system/lib/libxposed_art.so")) return 1;
    if (file_exists("/system/lib64/libxposed_art.so")) return 1;
    std::string maps = read_file_limited("/proc/self/maps", 4*1024*1024);
    if (!maps.empty()){
        if (contains_ci(maps, "xposed") || contains_ci(maps, "edxposed") ||
            contains_ci(maps, "lsposed") || contains_ci(maps, "substrate")) {
            return 1;
        }
    }
    if (g_vm && g_bridge_instance){
        JNIEnv* env=nullptr;
        bool attach=false;
        if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED){
            if (g_vm->AttachCurrentThread(&env,nullptr) != JNI_OK) return 0;
            attach=true;
        }
        jclass c = env->FindClass("de/robv/android/xposed/XposedBridge");
        if (c != nullptr){ env->DeleteLocalRef(c); if (attach) g_vm->DetachCurrentThread(); return 1; }
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (attach) g_vm->DetachCurrentThread();
    }
    return 0;
}

static int detect_magisk(){
    const char* paths[] = {
        "/sbin/.magisk", "/sbin/.core/mirror", "/sbin/.core/img",
        "/data/adb/magisk", "/data/adb/modules", "/cache/.disable_magisk",
        "/dev/.magisk.unblock", "/magisk", "/init.magisk.rc",
    };
    for (auto p : paths) if (file_exists(p)) return 1;
    return 0;
}

static int detect_su_binary(){
    const char* paths[] = {
        "/system/bin/su","/system/xbin/su","/sbin/su","/su/bin/su",
        "/system/sd/xbin/su","/system/bin/failsafe/su",
        "/data/local/xbin/su","/data/local/bin/su","/data/local/su",
        "/vendor/bin/su",
    };
    for (auto p : paths) if (file_exists(p)) return 1;
    return 0;
}

static int detect_vphonegaga(){
    const char* paths[] = {
        "/data/data/com.vphonegaga", "/data/user/0/com.vphonegaga",
        "/storage/emulated/0/vphonegaga",
    };
    for (auto p : paths) if (file_exists(p)) return 1;
    return 0;
}

static int detect_dualapp(){
    const char* paths[] = {
        "/data/data/com.lbe.parallel", "/data/user/0/com.lbe.parallel",
        "/data/data/com.excelliance.dualaid", "/data/user/0/com.excelliance.dualaid",
        "/data/data/com.parallel.space.lite", "/data/user/0/com.parallel.space.lite",
    };
    for (auto p : paths) if (file_exists(p)) return 1;
    return 0;
}

static int detect_emulator(){
    const char* paths[] = {
        "/dev/socket/qemud", "/dev/qemu_pipe",
        "/system/lib/libc_malloc_debug_qemu.so", "/sys/qemu_trace",
    };
    for (auto p : paths) if (file_exists(p)) return 1;
    return 0;
}

static std::string call_java_string(jmethodID mid);
static int call_java_int(jmethodID mid);

static int detect_debugger(){
    std::string status = read_file_limited("/proc/self/status", 8192);
    if (!status.empty()){
        auto pos = status.find("TracerPid:");
        if (pos != std::string::npos){
            size_t i = pos + 10;
            while (i<status.size() && (status[i]==' '||status[i]=='\t')) i++;
            int pid = 0;
            while (i<status.size() && status[i]>='0' && status[i]<='9'){ pid = pid*10 + (status[i]-'0'); i++; }
            if (pid != 0) return 1;
        }
    }
    if (g_vm && g_bridge_instance && g_is_debugger_attached && g_bridge_class){
        JNIEnv* env=nullptr;
        bool attach=false;
        if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED){
            if (g_vm->AttachCurrentThread(&env,nullptr) != JNI_OK) return 0;
            attach=true;
        }
        jboolean b = env->CallBooleanMethod(g_bridge_instance, g_is_debugger_attached);
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (attach) g_vm->DetachCurrentThread();
        if (b) return 1;
    }
    return 0;
}

static int compute_env_flags(){
    int flags = 0;
    if (detect_frida())       flags |= ENV_FLAG_FRIDA;
    if (detect_xposed())      flags |= ENV_FLAG_XPOSED;
    if (detect_magisk())      flags |= ENV_FLAG_MAGISK;
    if (detect_su_binary())   flags |= ENV_FLAG_SU_BINARY;
    if (detect_debugger())    flags |= ENV_FLAG_DEBUGGER;
    if (detect_vphonegaga())  flags |= ENV_FLAG_VPHONEGAGA;
    if (detect_dualapp())     flags |= ENV_FLAG_DUALAPP;
    if (detect_emulator())    flags |= ENV_FLAG_EMULATOR;
    std::string maps = read_file_limited("/proc/self/maps", 4*1024*1024);
    if (!maps.empty() && !contains_ci(maps, "libnative-lib.so")){
        flags |= ENV_FLAG_MAPS_SUS;
    }
    return flags;
}

// =============================================================================
// 8. JAVA CALLBACKS
// =============================================================================
static std::string call_java_string(jmethodID mid){
    if (!g_vm || !g_bridge_instance || !mid) return {};
    JNIEnv* env=nullptr;
    bool attach=false;
    if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED){
        if (g_vm->AttachCurrentThread(&env,nullptr) != JNI_OK) return {};
        attach=true;
    }
    auto js = (jstring)env->CallObjectMethod(g_bridge_instance, mid);
    std::string out;
    if (env->ExceptionCheck()){ env->ExceptionClear(); }
    else if (js){
        const char* c = env->GetStringUTFChars(js, nullptr);
        if (c){ out = c; env->ReleaseStringUTFChars(js, c); }
        env->DeleteLocalRef(js);
    }
    if (attach) g_vm->DetachCurrentThread();
    return out;
}

static int call_java_int(jmethodID mid){
    if (!g_vm || !g_bridge_instance || !mid) return 0;
    JNIEnv* env=nullptr;
    bool attach=false;
    if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED){
        if (g_vm->AttachCurrentThread(&env,nullptr) != JNI_OK) return 0;
        attach=true;
    }
    jint v = env->CallIntMethod(g_bridge_instance, mid);
    if (env->ExceptionCheck()){ env->ExceptionClear(); v = 0; }
    if (attach) g_vm->DetachCurrentThread();
    return (int)v;
}

// =============================================================================
// 9. ENVELOPE HELPERS
// =============================================================================
static std::string json_escape(const std::string& s){
    std::string out; out.reserve(s.size()+8);
    for (char c : s){
        switch (c){
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            case '\b': out += "\\b"; break;
            case '\f': out += "\\f"; break;
            default:
                if ((unsigned char)c < 0x20){
                    char b[8]; snprintf(b,sizeof(b),"\\u%04x",(unsigned)c);
                    out += b;
                } else out.push_back(c);
        }
    }
    return out;
}

static std::string json_unescape(const std::string& s){
    std::string out; out.reserve(s.size());
    for (size_t i=0;i<s.size();i++){
        char c = s[i];
        if (c=='\\' && i+1<s.size()){
            char n = s[++i];
            switch (n){
                case '"': out.push_back('"'); break;
                case '\\': out.push_back('\\'); break;
                case '/': out.push_back('/'); break;
                case 'n': out.push_back('\n'); break;
                case 'r': out.push_back('\r'); break;
                case 't': out.push_back('\t'); break;
                case 'b': out.push_back('\b'); break;
                case 'f': out.push_back('\f'); break;
                default: out.push_back(n);
            }
        } else out.push_back(c);
    }
    return out;
}

static std::string extract_json_field(const std::string& json, const std::string& key){
    std::string search = "\"" + key + "\"";
    auto pos = json.find(search);
    if (pos == std::string::npos) return {};
    pos = json.find(':', pos);
    if (pos == std::string::npos) return {};
    pos++;
    while (pos<json.size() && (json[pos]==' '||json[pos]=='\t'||json[pos]=='\n')) pos++;
    if (pos >= json.size()) return {};
    if (json[pos] == '"'){
        pos++;
        std::string raw;
        for (size_t i=pos;i<json.size();i++){
            if (json[i]=='\\' && i+1<json.size()){ raw.push_back(json[i]); raw.push_back(json[i+1]); i++; }
            else if (json[i]=='"'){ break; }
            else raw.push_back(json[i]);
        }
        return json_unescape(raw);
    }
    size_t end = pos;
    while (end<json.size() && json[end]!=',' && json[end]!='}') end++;
    return json.substr(pos, end-pos);
}

static const std::array<uint8_t,32>& get_aes_key(){
    static std::array<uint8_t,32> k = deobfuscate_hex(AES_HEX_OBF);
    return k;
}
static const std::array<uint8_t,32>& get_hmac_key(){
    static std::array<uint8_t,32> k = deobfuscate_hex(HMAC_HEX_OBF);
    return k;
}

// =============================================================================
// 10. JNI ONLOAD + INIT
// =============================================================================
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved){
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_drdev_faphousepro_security_NativeSecurityBridge_nativeInit(
        JNIEnv* env, jobject /*thiz*/, jobject bridge, jobject context){
    if (g_bridge_instance){ env->DeleteGlobalRef(g_bridge_instance); g_bridge_instance = nullptr; }
    if (g_app_context){ env->DeleteGlobalRef(g_app_context); g_app_context = nullptr; }
    if (g_bridge_class){ env->DeleteGlobalRef(g_bridge_class); g_bridge_class = nullptr; }

    if (!bridge){
        LOGE("nativeInit: bridge null");
        return;
    }
    g_bridge_instance = env->NewGlobalRef(bridge);

    if (context){
        jclass ctx_class = env->GetObjectClass(context);
        jmethodID get_app = env->GetMethodID(ctx_class, "getApplicationContext", "()Landroid/content/Context;");
        if (get_app){
            jobject app = env->CallObjectMethod(context, get_app);
            if (app){ g_app_context = env->NewGlobalRef(app); env->DeleteLocalRef(app); }
        }
        env->DeleteLocalRef(ctx_class);
    }

    jclass bridge_cls = env->GetObjectClass(bridge);
    g_bridge_class = (jclass)env->NewGlobalRef(bridge_cls);

    g_get_apk_sig  = env->GetMethodID(g_bridge_class, "javaGetApkSignatureSha256", "()Ljava/lang/String;");
    if (!g_get_apk_sig && env->ExceptionCheck()) env->ExceptionClear();
    g_get_version_name = env->GetMethodID(g_bridge_class, "javaGetVersionName", "()Ljava/lang/String;");
    if (!g_get_version_name && env->ExceptionCheck()) env->ExceptionClear();
    g_get_version_code = env->GetMethodID(g_bridge_class, "javaGetVersionCode", "()I");
    if (!g_get_version_code && env->ExceptionCheck()) env->ExceptionClear();
    g_get_device_model = env->GetMethodID(g_bridge_class, "javaGetDeviceModel", "()Ljava/lang/String;");
    if (!g_get_device_model && env->ExceptionCheck()) env->ExceptionClear();
    g_get_package_name = env->GetMethodID(g_bridge_class, "javaGetPackageName", "()Ljava/lang/String;");
    if (!g_get_package_name && env->ExceptionCheck()) env->ExceptionClear();
    g_get_installer    = env->GetMethodID(g_bridge_class, "javaGetInstallerPackage", "()Ljava/lang/String;");
    if (!g_get_installer && env->ExceptionCheck()) env->ExceptionClear();
    g_is_debugger_attached = env->GetMethodID(g_bridge_class, "javaIsDebuggerAttached", "()Z");
    if (!g_is_debugger_attached && env->ExceptionCheck()) env->ExceptionClear();

    env->DeleteLocalRef(bridge_cls);
    LOGI("nativeInit ok");
}

// =============================================================================
// 11. EXPORTS
// =============================================================================
extern "C" JNIEXPORT void JNICALL
Java_com_drdev_faphousepro_security_NativeSecurityBridge_setWidevineId(
        JNIEnv* env, jobject /*thiz*/, jbyteArray bytes){
    if (!bytes){ g_widevine_set = false; return; }
    jsize n = env->GetArrayLength(bytes);
    jbyte* buf = env->GetByteArrayElements(bytes, nullptr);
    if (buf && n > 0){
        size_t take = (size_t)n < 32 ? (size_t)n : 32;
        for (size_t i=0;i<take;i++) g_widevine_id[i] = (uint8_t)buf[i];
        for (size_t i=take;i<32;i++) g_widevine_id[i] = 0;
        g_widevine_set = true;
    }
    if (buf) env->ReleaseByteArrayElements(bytes, buf, JNI_ABORT);
    g_cached_device_id.clear();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_drdev_faphousepro_security_NativeSecurityBridge_getDeviceId(
        JNIEnv* env, jobject /*thiz*/){
    if (!g_cached_device_id.empty()) return env->NewStringUTF(g_cached_device_id.c_str());
    std::string pkg = call_java_string(g_get_package_name);
    std::string model = call_java_string(g_get_device_model);
    std::string ver = call_java_string(g_get_version_name);
    std::string sig = call_java_string(g_get_apk_sig);

    std::string material;
    material += "pkg=" + pkg + "|";
    material += "model=" + model + "|";
    material += "ver=" + ver + "|";
    material += "sig=" + sig + "|";
    material += "wv=";
    if (g_widevine_set){
        for (size_t i=0;i<32;i++){
            char b[3]; snprintf(b, sizeof(b), "%02x", g_widevine_id[i]);
            material += b;
        }
    } else {
        material += "none";
    }
    auto h = sha256_str(material);
    g_cached_device_id = hex_encode(h.data(), h.size());
    return env->NewStringUTF(g_cached_device_id.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_drdev_faphousepro_security_NativeSecurityBridge_getDeviceModel(
        JNIEnv* env, jobject /*thiz*/){
    return env->NewStringUTF(call_java_string(g_get_device_model).c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_drdev_faphousepro_security_NativeSecurityBridge_getApkSignature(
        JNIEnv* env, jobject /*thiz*/){
    return env->NewStringUTF(call_java_string(g_get_apk_sig).c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_drdev_faphousepro_security_NativeSecurityBridge_getVersionName(
        JNIEnv* env, jobject /*thiz*/){
    return env->NewStringUTF(call_java_string(g_get_version_name).c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_drdev_faphousepro_security_NativeSecurityBridge_getVersionCode(
        JNIEnv* env, jobject /*thiz*/){
    return (jint)call_java_int(g_get_version_code);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_drdev_faphousepro_security_NativeSecurityBridge_getEnvFlags(
        JNIEnv* env, jobject /*thiz*/){
    return (jint)compute_env_flags();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_drdev_faphousepro_security_NativeSecurityBridge_runIntegritySelfCheck(
        JNIEnv* env, jobject /*thiz*/,
        jstring expected_apk_sha, jstring expected_ver_name, jint expected_ver_code){
    const char* apk_exp_c = env->GetStringUTFChars(expected_apk_sha, nullptr);
    const char* ver_exp_c = env->GetStringUTFChars(expected_ver_name, nullptr);
    std::string apk_exp = apk_exp_c ? apk_exp_c : "";
    std::string ver_exp = ver_exp_c ? ver_exp_c : "";
    if (apk_exp_c) env->ReleaseStringUTFChars(expected_apk_sha, apk_exp_c);
    if (ver_exp_c) env->ReleaseStringUTFChars(expected_ver_name, ver_exp_c);

    std::string apk_actual = call_java_string(g_get_apk_sig);
    std::string ver_actual = call_java_string(g_get_version_name);
    int ver_code_actual = call_java_int(g_get_version_code);

    if (!apk_exp.empty() && apk_actual != apk_exp) return JNI_FALSE;
    if (ver_actual != ver_exp || ver_code_actual != (int)expected_ver_code) return JNI_FALSE;
    if (compute_env_flags() != 0) return JNI_FALSE;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_drdev_faphousepro_security_NativeSecurityBridge_encryptEnvelope(
        JNIEnv* env, jobject /*thiz*/, jstring plaintext_json){
    const char* c = env->GetStringUTFChars(plaintext_json, nullptr);
    std::string pt = c ? c : "";
    if (c) env->ReleaseStringUTFChars(plaintext_json, c);

    uint8_t nonce[12];
    int fd = open("/dev/urandom", O_RDONLY | O_CLOEXEC);
    if (fd < 0 || read(fd, nonce, 12) != 12){
        uint64_t t = (uint64_t)time(nullptr);
        for (int i=0;i<12;i++) nonce[i] = uint8_t((t >> (i%8*8)) & 0xFF) ^ (uint8_t)(i*31);
    }
    if (fd >= 0) close(fd);

    char ts_buf[24]; snprintf(ts_buf, sizeof(ts_buf), "%lld", (long long)time(nullptr));
    std::string ts_str(ts_buf);
    std::string nonce_b64 = b64url_encode(nonce, 12);
    while (!nonce_b64.empty() && nonce_b64.back()=='=') nonce_b64.pop_back();

    std::string aad_str = ts_str + ":" + nonce_b64;

    std::vector<uint8_t> ct(pt.size());
    uint8_t tag[16];
    aes_impl::gcm_encrypt(get_aes_key(), nonce,
                         reinterpret_cast<const uint8_t*>(aad_str.data()), aad_str.size(),
                         reinterpret_cast<const uint8_t*>(pt.data()), pt.size(),
                         ct.data(), tag);

    std::vector<uint8_t> combined;
    combined.reserve(12 + ct.size() + 16);
    combined.insert(combined.end(), nonce, nonce+12);
    combined.insert(combined.end(), ct.begin(), ct.end());
    combined.insert(combined.end(), tag, tag+16);

    std::string d_b64 = b64_encode(combined.data(), combined.size());
    auto payload_sha = sha256_buf(combined.data(), combined.size());
    std::string payload_sha_hex = hex_encode(payload_sha.data(), payload_sha.size());
    std::string signing = ts_str + ":" + nonce_b64 + ":" + payload_sha_hex;
    std::string sig_hex = hmac_sha256_hex(get_hmac_key(), signing);

    std::string env_json;
    env_json.reserve(256 + d_b64.size());
    env_json += "{";
    env_json += "\"d\":\"" + d_b64 + "\",";
    env_json += "\"s\":\"" + sig_hex + "\",";
    env_json += "\"t\":\"" + ts_str + "\",";
    env_json += "\"n\":\"" + nonce_b64 + "\"";
    env_json += "}";
    return env->NewStringUTF(env_json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_drdev_faphousepro_security_NativeSecurityBridge_decryptEnvelope(
        JNIEnv* env, jobject /*thiz*/, jstring envelope_json){
    const char* c = env->GetStringUTFChars(envelope_json, nullptr);
    std::string env_json = c ? c : "";
    if (c) env->ReleaseStringUTFChars(envelope_json, c);

    std::string d_b64 = extract_json_field(env_json, "d");
    std::string sig_hex = extract_json_field(env_json, "s");
    std::string ts_str = extract_json_field(env_json, "t");
    std::string nonce_b64 = extract_json_field(env_json, "n");

    if (d_b64.empty() || sig_hex.empty() || ts_str.empty() || nonce_b64.empty()){
        return env->NewStringUTF("{\"error\":\"bad_envelope\"}");
    }

    std::vector<uint8_t> combined = b64_decode(d_b64);
    if (combined.size() < 12 + 16){
        return env->NewStringUTF("{\"error\":\"short_cipher\"}");
    }

    auto payload_sha = sha256_buf(combined.data(), combined.size());
    std::string payload_sha_hex = hex_encode(payload_sha.data(), payload_sha.size());
    std::string signing = ts_str + ":" + nonce_b64 + ":" + payload_sha_hex;
    std::string expected_sig = hmac_sha256_hex(get_hmac_key(), signing);
    if (expected_sig != sig_hex){
        return env->NewStringUTF("{\"error\":\"bad_hmac\"}");
    }

    const uint8_t* nonce = combined.data();
    const uint8_t* ct = combined.data() + 12;
    size_t ct_len = combined.size() - 12 - 16;
    const uint8_t* tag = combined.data() + 12 + ct_len;

    std::string aad_str = ts_str + ":" + nonce_b64;

    std::vector<uint8_t> pt(ct_len);
    if (!aes_impl::gcm_decrypt(get_aes_key(), nonce,
                               reinterpret_cast<const uint8_t*>(aad_str.data()), aad_str.size(),
                               ct, ct_len, tag, pt.data())){
        return env->NewStringUTF("{\"error\":\"bad_gcm_tag\"}");
    }

    return env->NewStringUTF(std::string(reinterpret_cast<char*>(pt.data()), pt.size()).c_str());
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_drdev_faphousepro_security_NativeSecurityBridge_getCertPins(
        JNIEnv* env, jobject /*thiz*/){
    const char* pins[] = {
        "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
    };
    size_t n = sizeof(pins)/sizeof(pins[0]);
    jclass str_class = env->FindClass("java/lang/String");
    jobjectArray arr = env->NewObjectArray((jsize)n, str_class, nullptr);
    for (size_t i=0;i<n;i++){
        jstring s = env->NewStringUTF(pins[i]);
        env->SetObjectArrayElement(arr, (jsize)i, s);
        env->DeleteLocalRef(s);
    }
    return arr;
}