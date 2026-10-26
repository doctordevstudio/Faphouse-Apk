(function () {
    'use strict';
    if (window.FHPPlayer) return;

    var host = null;
    var video = null;
    var wrap = null;
    var state = {
        sessionId: '',
        videoUrl: '',
        qualities: [],
        subs: [],
        currentQuality: '',
        controlsTimer: null
    };

    function log(msg) {
        try { if (window.FHPBridge) window.FHPBridge.log(String(msg)); } catch (e) {}
    }

    function fmt(sec) {
        if (!isFinite(sec) || sec < 0) sec = 0;
        var s = Math.floor(sec % 60);
        var m = Math.floor((sec / 60) % 60);
        var h = Math.floor(sec / 3600);
        var ss = s < 10 ? '0' + s : '' + s;
        var mm = m < 10 ? '0' + m : '' + m;
        return h > 0 ? (h + ':' + mm + ':' + ss) : (mm + ':' + ss);
    }

    function findHost() {
        return document.querySelector('.video__main')
            || document.querySelector('.video__wrap')
            || document.querySelector('.video')
            || document.body;
    }

    function svg(d) { return '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="' + d + '"/></svg>'; }

    var ICON = {
        play: svg('M8 5v14l11-7z'),
        pause: svg('M6 19h4V5H6v14zm8-14v14h4V5h-4z'),
        volume: svg('M3 9v6h4l5 5V4L7 9H3z'),
        mute: svg('M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z'),
        download: svg('M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z'),
        pip: svg('M19 11h-8v6h8v-6zm4 10V3H1v18h22zm-2-1.98H3V4.97h18v14.05z'),
        fullscreen: svg('M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z'),
        exitfull: svg('M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z')
    };

    function buildPlayerDom() {
        var w = document.createElement('div');
        w.id = 'fhp-player-wrap';
        w.className = 'loading paused';
        w.innerHTML = ''
            + '<video id="fhp-player" playsinline preload="metadata"></video>'
            + '<div id="fhp-spinner"></div>'
            + '<div id="fhp-center-play">' + ICON.play + '</div>'
            + '<div id="fhp-error-msg">'
            + '  <div class="fhp-err-title">Stream unavailable</div>'
            + '  <div class="fhp-err-body">The stream session may have expired.</div>'
            + '  <button class="fhp-retry">Retry</button>'
            + '</div>'
            + '<div id="fhp-controls">'
            + '  <div id="fhp-progress-row">'
            + '    <button id="fhp-playbtn" aria-label="Play">' + ICON.play + '</button>'
            + '    <div id="fhp-progress">'
            + '      <div id="fhp-progress-buffered"></div>'
            + '      <div id="fhp-progress-fill"></div>'
            + '      <div id="fhp-progress-thumb"></div>'
            + '    </div>'
            + '    <span id="fhp-time">00:00 / 00:00</span>'
            + '  </div>'
            + '  <div id="fhp-btn-row">'
            + '    <button class="fhp-btn" id="fhp-volbtn" aria-label="Volume">' + ICON.volume + '</button>'
            + '    <button class="fhp-btn fhp-btn-gold" id="fhp-dlbtn" aria-label="Download">' + ICON.download + '</button>'
            + '    <span id="fhp-spacer"></span>'
            + '    <div id="fhp-quality-wrap">'
            + '      <button class="fhp-btn" id="fhp-quality-btn">'
            + '        <span id="fhp-quality-label">AUTO</span>'
            + '        <span class="fhp-caret">▼</span>'
            + '      </button>'
            + '      <div id="fhp-quality-menu"></div>'
            + '    </div>'
            + '    <button class="fhp-btn" id="fhp-pipbtn" aria-label="Picture in Picture">' + ICON.pip + '</button>'
            + '    <button class="fhp-btn" id="fhp-fsbtn" aria-label="Fullscreen">' + ICON.fullscreen + '</button>'
            + '  </div>'
            + '</div>';
        return w;
    }

    function buildPlaceholder() {
        var p = document.createElement('div');
        p.id = 'fhp-player-wrap';
        p.innerHTML = ''
            + '<div id="fhp-placeholder">'
            + '  <div class="fhp-ph-inner">'
            + '    <div class="fhp-ph-spinner"></div>'
            + '    <div>Preparing player…</div>'
            + '  </div>'
            + '</div>';
        return p;
    }

    function attachHandlers() {
        var playBtn = wrap.querySelector('#fhp-playbtn');
        var centerPlay = wrap.querySelector('#fhp-center-play');
        var volBtn = wrap.querySelector('#fhp-volbtn');
        var dlBtn = wrap.querySelector('#fhp-dlbtn');
        var pipBtn = wrap.querySelector('#fhp-pipbtn');
        var fsBtn = wrap.querySelector('#fhp-fsbtn');
        var progress = wrap.querySelector('#fhp-progress');
        var progressFill = wrap.querySelector('#fhp-progress-fill');
        var progressBuffered = wrap.querySelector('#fhp-progress-buffered');
        var progressThumb = wrap.querySelector('#fhp-progress-thumb');
        var timeLbl = wrap.querySelector('#fhp-time');
        var qBtn = wrap.querySelector('#fhp-quality-btn');
        var qMenu = wrap.querySelector('#fhp-quality-menu');
        var retryBtn = wrap.querySelector('.fhp-retry');

        function togglePlay() {
            if (video.paused) video.play().catch(function(e){ log('play rejected '+e); });
            else video.pause();
            pokeControls();
        }

        playBtn.addEventListener('click', function(e){ e.stopPropagation(); togglePlay(); });
        centerPlay.addEventListener('click', function(e){ e.stopPropagation(); togglePlay(); });
        video.addEventListener('click', function(e){ e.stopPropagation(); togglePlay(); });
        video.addEventListener('dblclick', function(e){ e.stopPropagation(); toggleFullscreen(); });

        video.addEventListener('play', function(){
            wrap.classList.remove('paused');
            wrap.classList.add('playing');
            playBtn.innerHTML = ICON.pause;
        });
        video.addEventListener('pause', function(){
            wrap.classList.add('paused');
            wrap.classList.remove('playing');
            playBtn.innerHTML = ICON.play;
            wrap.classList.add('controls-visible');
        });
        video.addEventListener('waiting', function(){ wrap.classList.add('buffering'); });
        video.addEventListener('playing', function(){ wrap.classList.remove('buffering'); });
        video.addEventListener('canplay', function(){ wrap.classList.remove('loading','buffering'); });

        video.addEventListener('timeupdate', function(){
            var cur = video.currentTime || 0;
            var dur = video.duration || 0;
            var pct = dur > 0 ? (cur / dur) * 100 : 0;
            progressFill.style.width = pct + '%';
            progressThumb.style.left = pct + '%';
            timeLbl.textContent = fmt(cur) + ' / ' + fmt(dur);
            try {
                var buf = video.buffered;
                if (buf.length > 0 && dur > 0) {
                    var b = buf.end(buf.length - 1);
                    progressBuffered.style.width = ((b / dur) * 100) + '%';
                }
            } catch (e) {}
        });

        video.addEventListener('loadedmetadata', function(){
            timeLbl.textContent = '00:00 / ' + fmt(video.duration || 0);
        });

        video.addEventListener('error', function(){
            wrap.classList.remove('loading','buffering');
            wrap.classList.add('error');
            try { window.FHPBridge.onSessionExpired(); } catch (e) {}
        });

        function seekAt(evt) {
            var rect = progress.getBoundingClientRect();
            var x = (evt.clientX !== undefined ? evt.clientX : evt.touches[0].clientX) - rect.left;
            var pct = Math.max(0, Math.min(1, x / rect.width));
            if (video.duration) video.currentTime = pct * video.duration;
        }

        var seeking = false;
        progress.addEventListener('mousedown', function(e){ seeking = true; seekAt(e); });
        progress.addEventListener('mousemove', function(e){ if (seeking) seekAt(e); });
        document.addEventListener('mouseup', function(){ seeking = false; });
        progress.addEventListener('touchstart', function(e){ seeking = true; seekAt(e); }, {passive: true});
        progress.addEventListener('touchmove', function(e){ if (seeking) seekAt(e); }, {passive: true});
        progress.addEventListener('touchend', function(){ seeking = false; });

        volBtn.addEventListener('click', function(e){
            e.stopPropagation();
            video.muted = !video.muted;
            volBtn.innerHTML = video.muted ? ICON.mute : ICON.volume;
        });

        dlBtn.addEventListener('click', function(e){
            e.stopPropagation();
            try {
                if (window.FHPBridge && window.FHPBridge.onDownloadRequested) {
                    window.FHPBridge.onDownloadRequested(window.location.href);
                }
            } catch (err) {}
        });

        pipBtn.addEventListener('click', function(e){
            e.stopPropagation();
            try {
                if (document.pictureInPictureElement) document.exitPictureInPicture();
                else if (video.requestPictureInPicture) video.requestPictureInPicture();
            } catch (err) {}
        });
        if (!('pictureInPictureEnabled' in document) || !document.pictureInPictureEnabled) {
            pipBtn.style.display = 'none';
        }

        fsBtn.addEventListener('click', function(e){ e.stopPropagation(); toggleFullscreen(); });

        retryBtn.addEventListener('click', function(e){
            e.stopPropagation();
            wrap.classList.remove('error');
            wrap.classList.add('loading');
            try { window.FHPBridge.onSessionExpired(); } catch (err) {}
        });

        qBtn.addEventListener('click', function(e){
            e.stopPropagation();
            qMenu.classList.toggle('open');
        });
        document.addEventListener('click', function(){ qMenu.classList.remove('open'); });

        function toggleFullscreen() {
            if (document.fullscreenElement) document.exitFullscreen();
            else if (wrap.requestFullscreen) wrap.requestFullscreen();
        }

        document.addEventListener('fullscreenchange', function(){
            var fs = !!document.fullscreenElement;
            wrap.classList.toggle('is-fullscreen', fs);
            fsBtn.innerHTML = fs ? ICON.exitfull : ICON.fullscreen;
            try { window.FHPBridge.onFullscreenChanged(fs); } catch (e) {}
        });

        function pokeControls() {
            wrap.classList.add('controls-visible');
            if (state.controlsTimer) clearTimeout(state.controlsTimer);
            state.controlsTimer = setTimeout(function(){
                if (!video.paused) wrap.classList.remove('controls-visible');
            }, 2500);
        }
        wrap.addEventListener('mousemove', pokeControls);
        wrap.addEventListener('touchstart', pokeControls, {passive: true});
    }

    function buildQualityMenu() {
        var menu = wrap.querySelector('#fhp-quality-menu');
        var label = wrap.querySelector('#fhp-quality-label');
        if (!menu || !label) return;
        menu.innerHTML = '';

        var autoItem = document.createElement('div');
        autoItem.className = 'fhp-q-item' + (state.currentQuality === '' ? ' active' : '');
        autoItem.innerHTML = '<span>Auto</span><span>HD</span>';
        autoItem.addEventListener('click', function(e){
            e.stopPropagation();
            selectQuality('');
        });
        menu.appendChild(autoItem);

        state.qualities.forEach(function(q){
            var item = document.createElement('div');
            item.className = 'fhp-q-item' + (state.currentQuality === q.label ? ' active' : '');
            var mb = q.sizeMb ? '~' + Number(q.sizeMb).toFixed(0) + ' MB' : '';
            item.innerHTML = '<span>' + q.label + '</span><span>' + mb + '</span>';
            item.addEventListener('click', function(e){
                e.stopPropagation();
                selectQuality(q.label);
            });
            menu.appendChild(item);
        });

        label.textContent = state.currentQuality ? state.currentQuality : 'AUTO';
    }

    function selectQuality(label) {
        state.currentQuality = label;
        var url;
        if (!label) {
            url = 'https://fhp.local/hls/' + state.sessionId + '/master.m3u8';
        } else {
            var match = null;
            for (var i = 0; i < state.qualities.length; i++) {
                if (state.qualities[i].label === label) { match = state.qualities[i]; break; }
            }
            if (!match) return;
            url = match.proxyUrl;
        }

        var curTime = video.currentTime || 0;
        var wasPlaying = !video.paused;
        video.src = url;
        video.load();
        try { video.currentTime = curTime; } catch (e) {}
        if (wasPlaying) video.play().catch(function(){});

        buildQualityMenu();
        var menu = wrap.querySelector('#fhp-quality-menu');
        if (menu) menu.classList.remove('open');
    }

    function mount(payload) {
        try {
            if (!payload) return;
            state.sessionId = payload.sessionId || '';
            state.videoUrl = payload.videoUrl || location.href;
            state.qualities = payload.qualities || [];
            state.subs = payload.subs || [];
            state.currentQuality = '';

            destroy(true);

            host = findHost();
            if (!host) return;

            var ph = document.getElementById('fhp-player-wrap');
            var w = buildPlayerDom();
            video = w.querySelector('#fhp-player');

            if (state.subs && state.subs.length) {
                for (var i = 0; i < state.subs.length; i++) {
                    var s = state.subs[i];
                    var t = document.createElement('track');
                    t.kind = 'subtitles';
                    t.label = s.label || s.lang;
                    t.srclang = (s.lang || 'en').toLowerCase().slice(0,2);
                    t.src = s.proxyUrl;
                    video.appendChild(t);
                }
            }

            if (ph && ph.parentNode) ph.parentNode.replaceChild(w, ph);
            else host.insertBefore(w, host.firstChild);
            wrap = w;

            video.src = 'https://fhp.local/hls/' + state.sessionId + '/master.m3u8';

            attachHandlers();
            buildQualityMenu();

            video.addEventListener('canplay', function(){
                wrap.classList.remove('loading');
            }, {once: true});
            video.addEventListener('loadedmetadata', function(){
                wrap.classList.remove('loading');
            }, {once: true});
            setTimeout(function(){
                if (wrap && !wrap.classList.contains('error')) {
                    wrap.classList.remove('loading');
                }
            }, 8000);

            var hint = document.createElement('div');
            hint.id = 'fhp-related-hint';
            hint.textContent = 'Related videos below ↓';
            hint.addEventListener('click', function(e){
                e.stopPropagation();
                var rel = document.querySelector('.video__related');
                if (rel) rel.scrollIntoView({behavior:'smooth', block:'start'});
                else window.scrollBy({top: window.innerHeight, behavior:'smooth'});
            });
            if (wrap.parentNode) {
                wrap.parentNode.insertBefore(hint, wrap.nextSibling);
            }
        } catch (e) {
            log('mount error: ' + e.message);
        }
    }

    function refresh(newSessionId, newQualities) {
        try {
            if (!wrap || !video) return;
            var curTime = video.currentTime || 0;
            var wasPlaying = !video.paused;
            state.sessionId = newSessionId;
            if (newQualities && newQualities.length) state.qualities = newQualities;
            state.currentQuality = '';
            wrap.classList.remove('error');
            wrap.classList.add('loading');
            var url = 'https://fhp.local/hls/' + state.sessionId + '/master.m3u8';
            video.src = url;
            video.load();
            try { video.currentTime = curTime; } catch (e) {}
            if (wasPlaying) video.play().catch(function(){});
            buildQualityMenu();
        } catch (e) {
            log('refresh error: ' + e.message);
        }
    }

    function destroy(silent) {
        try {
            if (video) {
                try { video.pause(); } catch (e) {}
                try { video.src = ''; } catch (e) {}
            }
            if (wrap && wrap.parentNode) wrap.parentNode.removeChild(wrap);
            var hint = document.getElementById('fhp-related-hint');
            if (hint && hint.parentNode) hint.parentNode.removeChild(hint);
            var ph = document.getElementById('fhp-player-wrap');
            if (ph && !silent && ph.parentNode) ph.parentNode.removeChild(ph);
        } catch (e) {}
        wrap = null;
        video = null;
        state.currentQuality = '';
    }

    window.FHPPlayer = {
        mount: mount,
        refresh: refresh,
        destroy: destroy
    };
})();