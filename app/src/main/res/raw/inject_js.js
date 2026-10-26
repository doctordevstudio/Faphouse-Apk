(function () {
    'use strict';

    var BLOCK_SELECTORS = [
        '[data-test-id="header-join-btn"]',
        '[data-test-id="header-signin-btn"]',
        'a[href="#signin"]',
        'a[href^="/join"]',
        'a[href^="/offers/fanclub/"]',
        '.video-purchase',
        '.video-purchase__menu',
        '.video-purchase__complete-wrap',
        '.video-purchase__menu-toggle',
        '.video-purchase__controls',
        '.become-member',
        '.become-member-card',
        '.become-member-banner',
        '.paywall',
        '.locked-video',
        '.js-paywall',
        '[data-el="AnchorGoToJoin"]',
        '[data-el="OpenUpgradeExistingSubscriptionModal"]',
        '[data-el="SubscriptionModal"]',
        '[data-el="ButtonBuy"]',
        '.fanclub-promo-banner',
        '.fh-fan-club-promo',
        '.fh-join-banner',
        '.fh-bottom-sticky-banner',
        '.fh-bottom-sticky-banner__container',
        '.fh-bottom-sticky-banner--mobile',
        '[data-testid="buy-button"]',
        '[data-testid="fan-join-button"]',
        '.video-info-details__actions-not-buyed',
        '.video-info-details__actions-access',
        '.video-info-details__actions-title',
        '[data-testid="download-button"]',
        '.video-related-promo',
        '.video-related-join-cta'
    ];

    var PROTECTED_SELECTORS = [
        '.modal', '.cookie-banner', '.cookie-wall', '.age-verification', '.age-gate',
        '[data-el="ConsentModal"]', '[data-el="CookieModal"]',
        'video', '.video-player', '.video__player', '#video-player',
        '#fhp-player-wrap', '#fhp-player'
    ];

    function isProtected(node) {
        if (!node || node.nodeType !== 1) return false;
        for (var i = 0; i < PROTECTED_SELECTORS.length; i++) {
            try { if (node.closest(PROTECTED_SELECTORS[i])) return true; } catch (e) {}
        }
        return false;
    }

    function hide(el) {
        if (!el || el.nodeType !== 1) return;
        if (isProtected(el)) return;
        el.style.setProperty('display', 'none', 'important');
        el.style.setProperty('visibility', 'hidden', 'important');
        el.style.setProperty('pointer-events', 'none', 'important');
    }

    function sweep() {
        for (var i = 0; i < BLOCK_SELECTORS.length; i++) {
            var nodes;
            try { nodes = document.querySelectorAll(BLOCK_SELECTORS[i]); } catch (e) { continue; }
            for (var j = 0; j < nodes.length; j++) hide(nodes[j]);
        }
    }

    function blockClick(ev) {
        var t = ev.target;
        if (!t || !t.closest) return;
        var forbidden = t.closest([
            'a[href^="/join"]', 'a[href^="/offers/fanclub/"]', 'a[href="#signin"]',
            '[data-el="AnchorGoToJoin"]', '[data-el="SubscriptionModal"]',
            '[data-el="OpenUpgradeExistingSubscriptionModal"]',
            '[data-testid="buy-button"]', '[data-testid="fan-join-button"]'
        ].join(','));
        if (forbidden) { ev.preventDefault(); ev.stopPropagation(); }
    }

    sweep();

    var observer = new MutationObserver(function (mutations) {
        for (var i = 0; i < mutations.length; i++) {
            var m = mutations[i];
            if (m.type === 'childList' && m.addedNodes) {
                for (var j = 0; j < m.addedNodes.length; j++) {
                    if (m.addedNodes[j].nodeType === 1) { sweep(); return; }
                }
            }
        }
    });
    if (document.body) {
        observer.observe(document.body, { childList: true, subtree: true });
    } else {
        document.addEventListener('DOMContentLoaded', function () {
            observer.observe(document.body, { childList: true, subtree: true });
        });
    }

    document.addEventListener('click', blockClick, true);
    document.addEventListener('auxclick', blockClick, true);
    document.addEventListener('contextmenu', blockClick, true);

    var lastUrl = location.href;
    setInterval(function () {
        if (location.href !== lastUrl) {
            lastUrl = location.href;
            try {
                if (window.FHPBridge && window.FHPBridge.onSpaNavigation) {
                    window.FHPBridge.onSpaNavigation(location.href);
                }
            } catch (e) {}
        }
    }, 600);
})();