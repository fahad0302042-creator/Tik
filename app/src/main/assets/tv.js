/*
 * TikTV — injected into TikTok's own web page.
 *
 * The app never parses TikTok's data: it only nudges the page the way a keyboard,
 * a mouse or a finger would. Everything here is defensive — each DOM lookup is
 * wrapped so a TikTok redesign can only make one strategy fail, never break the page.
 */
(function () {
    'use strict';

    if (window.Tv && window.Tv.__tikTv) return;

    var VERSION = '1.2.0';
    var bridge = function () {
        try {
            return window.TvBridge || null;
        } catch (e) {
            return null;
        }
    };

    function log(message) {
        try {
            var b = bridge();
            if (b) b.log(String(message));
        } catch (e) {
        }
    }

    function toast(message) {
        try {
            var b = bridge();
            if (b) b.onToast(String(message));
        } catch (e) {
        }
    }

    var cfg = {
        autoplayNext: true,
        cinema: false
    };

    /* ------------------------------------------------------------------ helpers */

    function sel(selector, root) {
        try {
            return (root || document).querySelector(selector);
        } catch (e) {
            return null;
        }
    }

    function selAll(selector, root) {
        try {
            return Array.prototype.slice.call((root || document).querySelectorAll(selector));
        } catch (e) {
            return [];
        }
    }

    function visible(el) {
        if (!el || !el.getBoundingClientRect) return false;
        var r = el.getBoundingClientRect();
        if (r.width < 24 || r.height < 24) return false;
        var style = window.getComputedStyle(el);
        if (!style) return false;
        if (style.display === 'none' || style.visibility === 'hidden') return false;
        if (parseFloat(style.opacity || '1') < 0.05) return false;
        return true;
    }

    function onScreen(el) {
        if (!el || !el.getBoundingClientRect) return false;
        var r = el.getBoundingClientRect();
        return r.bottom > 0 && r.top < window.innerHeight && r.right > 0 && r.left < window.innerWidth;
    }

    /** The biggest video currently on screen is the one the user is watching. */
    function mainVideo() {
        var best = null;
        var bestScore = -1;
        var videos = selAll('video');
        for (var i = 0; i < videos.length; i++) {
            var v = videos[i];
            if (!v.getBoundingClientRect) continue;
            var r = v.getBoundingClientRect();
            if (r.width < 100 || r.height < 100) continue;
            if (!onScreen(v)) continue;
            var score = r.width * r.height;
            if (v.readyState >= 2) score *= 2;
            if (!v.paused) score *= 1.5;
            if (score > bestScore) {
                bestScore = score;
                best = v;
            }
        }
        if (best) return best;
        return videos.length ? videos[0] : null;
    }

    function srcOf(video) {
        if (!video) return '';
        return String(video.currentSrc || video.src || video.getAttribute('src') || '');
    }

    function click(el) {
        try {
            el.click();
            return true;
        } catch (e) {
            try {
                var r = el.getBoundingClientRect();
                var x = r.left + r.width / 2;
                var y = r.top + r.height / 2;
                ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(function (type) {
                    var ev = new MouseEvent(type, {
                        bubbles: true,
                        cancelable: true,
                        view: window,
                        clientX: x,
                        clientY: y
                    });
                    el.dispatchEvent(ev);
                });
                return true;
            } catch (e2) {
                return false;
            }
        }
    }

    /* ------------------------------------------------------------------ feed stepping */

    var NEXT_SELECTORS = [
        'button[data-e2e="arrow-right"]',
        '[data-e2e="arrow-right"]',
        'button[data-e2e="arrow-down"]',
        '[data-e2e="arrow-down"]',
        'button[data-e2e="feed-next"]',
        '[aria-label="Next video"]',
        '[aria-label="next"]'
    ];

    var PREV_SELECTORS = [
        'button[data-e2e="arrow-left"]',
        '[data-e2e="arrow-left"]',
        'button[data-e2e="arrow-up"]',
        '[data-e2e="arrow-up"]',
        '[aria-label="Previous video"]',
        '[aria-label="previous"]'
    ];

    function clickArrow(direction) {
        var selectors = direction > 0 ? NEXT_SELECTORS : PREV_SELECTORS;
        for (var i = 0; i < selectors.length; i++) {
            var el = sel(selectors[i]);
            if (el && visible(el)) {
                if (click(el)) return 'arrow:' + selectors[i];
            }
        }
        return null;
    }

    function sendArrowKey(direction) {
        var key = direction > 0 ? 'ArrowDown' : 'ArrowUp';
        var code = direction > 0 ? 40 : 38;
        var targets = [document.activeElement, document.body, document.documentElement, mainVideo()];
        var sent = false;
        for (var i = 0; i < targets.length; i++) {
            var target = targets[i];
            if (!target) continue;
            try {
                ['keydown', 'keypress', 'keyup'].forEach(function (type) {
                    var ev = new KeyboardEvent(type, {
                        key: key,
                        code: key,
                        keyCode: code,
                        which: code,
                        bubbles: true,
                        cancelable: true
                    });
                    target.dispatchEvent(ev);
                });
                sent = true;
            } catch (e) {
            }
        }
        return sent ? 'key:' + key : null;
    }

    /** Synthetic finger swipe — this is what the mobile web feed listens to. */
    function swipe(direction) {
        var video = mainVideo();
        var host = video || document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
        if (!host) return null;

        var r = host.getBoundingClientRect();
        var x = Math.round(r.left + r.width / 2);
        var yStart = Math.round(r.top + r.height * (direction > 0 ? 0.78 : 0.22));
        var yEnd = Math.round(r.top + r.height * (direction > 0 ? 0.18 : 0.82));

        try {
            var makeTouch = function (x0, y0) {
                return new Touch({
                    identifier: 1,
                    target: host,
                    clientX: x0,
                    clientY: y0,
                    pageX: x0,
                    pageY: y0
                });
            };
            var fire = function (type, y) {
                var touch = makeTouch(x, y);
                var list = type === 'touchend' || type === 'touchcancel' ? [] : [touch];
                var ev = new TouchEvent(type, {
                    bubbles: true,
                    cancelable: true,
                    cancelable_: true,
                    touches: list,
                    targetTouches: list,
                    changedTouches: [touch]
                });
                host.dispatchEvent(ev);
            };

            fire('touchstart', yStart);
            var steps = 8;
            for (var i = 1; i <= steps; i++) {
                fire('touchmove', Math.round(yStart + (yEnd - yStart) * (i / steps)));
            }
            fire('touchend', yEnd);
            return 'swipe';
        } catch (e) {
            return null;
        }
    }

    function findScroller(from) {
        var el = from;
        var guard = 0;
        while (el && el !== document.body && el !== document.documentElement && guard < 40) {
            var style = window.getComputedStyle(el);
            var overflow = style ? (style.overflowY || style.overflow) : '';
            if ((overflow === 'auto' || overflow === 'scroll') && el.scrollHeight > el.clientHeight + 40) {
                return el;
            }
            el = el.parentElement;
            guard++;
        }
        return null;
    }

    function scrollFeed(direction) {
        var scroller = findScroller(mainVideo());
        if (scroller) {
            try {
                scroller.scrollBy({ top: direction * Math.round(scroller.clientHeight * 0.92), behavior: 'smooth' });
                return 'scroll:container';
            } catch (e) {
            }
        }
        try {
            window.scrollBy({ top: direction * Math.round(window.innerHeight * 0.92), behavior: 'smooth' });
            return 'scroll:window';
        } catch (e) {
            window.scrollBy(0, direction * Math.round(window.innerHeight * 0.92));
            return 'scroll:window';
        }
    }

    var stepping = false;

    /**
     * Tries every known way to advance the feed and stops at the first one that
     * actually swapped the video.
     */
    function step(direction) {
        if (stepping) return;
        stepping = true;

        var before = srcOf(mainVideo());
        var tried = [];

        var finish = function (ok, how) {
            stepping = false;
            log('step ' + (direction > 0 ? 'next' : 'prev') + ' ' + (ok ? 'ok' : 'failed') +
                ' tried=[' + tried.join(',') + ']' + (how ? ' via ' + how : ''));
            if (ok) ensurePlaying();
        };

        var attempt = function (how, delay, next) {
            if (how) tried.push(how);
            window.setTimeout(function () {
                if (srcOf(mainVideo()) !== before) {
                    finish(true, how);
                } else {
                    next();
                }
            }, delay);
        };

        var step1 = clickArrow(direction);
        attempt(step1, 260, function () {
            var step2 = sendArrowKey(direction);
            attempt(step2, 260, function () {
                var step3 = swipe(direction);
                attempt(step3, 420, function () {
                    var step4 = scrollFeed(direction);
                    attempt(step4, 520, function () {
                        finish(false, null);
                    });
                });
            });
        });
    }

    function ensurePlaying() {
        var v = mainVideo();
        if (!v) return;
        try {
            if (v.paused) {
                var p = v.play();
                if (p && p.catch) p.catch(function () {
                });
            }
        } catch (e) {
        }
    }

    /* ------------------------------------------------------------------ playback */

    function toggle() {
        var v = mainVideo();
        if (!v) {
            toast('No video on this page');
            return 'none';
        }
        try {
            if (v.paused) {
                var p = v.play();
                if (p && p.catch) p.catch(function () {
                });
                return 'play';
            }
            v.pause();
            return 'pause';
        } catch (e) {
            return 'error';
        }
    }

    function play() {
        var v = mainVideo();
        if (!v) return;
        try {
            var p = v.play();
            if (p && p.catch) p.catch(function () {
            });
        } catch (e) {
        }
    }

    function pause() {
        var v = mainVideo();
        if (!v) return;
        try {
            v.pause();
        } catch (e) {
        }
    }

    function seek(seconds) {
        var v = mainVideo();
        if (!v) return;
        try {
            var duration = isFinite(v.duration) ? v.duration : 0;
            var target = v.currentTime + Number(seconds || 0);
            if (target < 0) target = 0;
            if (duration > 0 && target > duration - 0.4) target = duration - 0.4;
            v.currentTime = target;
        } catch (e) {
        }
    }

    function mute() {
        var v = mainVideo();
        if (!v) return;
        try {
            v.muted = !v.muted;
            toast(v.muted ? 'Muted' : 'Sound on');
        } catch (e) {
        }
    }

    function scrollPage(dy) {
        try {
            window.scrollBy(0, Number(dy || 0));
        } catch (e) {
        }
    }

    /* ------------------------------------------------------------------ cinema mode */

    var CINEMA_STYLE_ID = 'tik-tv-cinema-style';
    var patched = [];
    var promotedVideo = null;

    function clearCinema() {
        for (var i = 0; i < patched.length; i++) {
            try {
                var item = patched[i];
                if (item.el.getAttribute('style') === null) {
                    item.el.removeAttribute('style');
                } else {
                    item.el.setAttribute('style', item.css);
                }
            } catch (e) {
            }
        }
        patched = [];
        promotedVideo = null;
        try {
            var style = document.getElementById(CINEMA_STYLE_ID);
            if (style && style.parentNode) style.parentNode.removeChild(style);
        } catch (e) {
        }
    }

    function remember(el) {
        for (var i = 0; i < patched.length; i++) {
            if (patched[i].el === el) return;
        }
        patched.push({ el: el, css: el.getAttribute('style') || '' });
    }

    /**
     * Lifts the current video out of TikTok's layout and pins it to the viewport.
     * Reversible: press the green button again and the original styles come back.
     */
    function applyCinema(enabled) {
        cfg.cinema = !!enabled;
        clearCinema();
        if (!cfg.cinema) return;

        try {
            var style = document.createElement('style');
            style.id = CINEMA_STYLE_ID;
            style.textContent =
                'html,body{background:#000 !important;overflow:hidden !important;}' +
                '::-webkit-scrollbar{display:none !important;}';
            document.documentElement.appendChild(style);
        } catch (e) {
        }

        var video = mainVideo();
        if (!video) {
            log('cinema: no video yet');
            return;
        }

        try {
            var node = video.parentElement;
            var depth = 0;
            while (node && node !== document.body && depth < 12) {
                var style = window.getComputedStyle(node);
                var rect = node.getBoundingClientRect();
                if (style && style.position === 'fixed' && rect.width > window.innerWidth * 0.9) break;
                remember(node);
                node.style.setProperty('overflow', 'visible', 'important');
                node.style.setProperty('clip-path', 'none', 'important');
                node = node.parentElement;
                depth++;
            }

            remember(video);
            var s = video.style;
            s.setProperty('position', 'fixed', 'important');
            s.setProperty('top', '0', 'important');
            s.setProperty('left', '0', 'important');
            s.setProperty('width', '100vw', 'important');
            s.setProperty('height', '100vh', 'important');
            s.setProperty('max-width', 'none', 'important');
            s.setProperty('max-height', 'none', 'important');
            s.setProperty('object-fit', 'contain', 'important');
            s.setProperty('background', '#000', 'important');
            s.setProperty('z-index', '2147483000', 'important');
            try {
                video.controls = true;
            } catch (e) {
            }
            promotedVideo = video;
            log('cinema: on');
        } catch (e) {
            log('cinema error: ' + e);
        }
    }

    /* ------------------------------------------------------------------ text entry */

    var textTargets = {};
    var textTargetSeq = 0;

    function isTextEntry(el) {
        if (!el || !el.tagName) return false;
        var tag = el.tagName.toLowerCase();
        if (tag !== 'input' && tag !== 'textarea' && !el.isContentEditable) return false;
        if (tag === 'input') {
            var type = (el.type || 'text').toLowerCase();
            var skip = ['checkbox', 'radio', 'submit', 'button', 'file', 'range', 'color',
                'hidden', 'image', 'reset', 'date', 'time'];
            if (skip.indexOf(type) >= 0) return false;
        }
        return true;
    }

    function onFocusIn(event) {
        var el = event.target;
        if (!isTextEntry(el)) return;

        textTargetSeq += 1;
        var id = 'f' + textTargetSeq;
        textTargets[id] = el;

        var hint = el.getAttribute('placeholder') || el.getAttribute('aria-label') || '';
        var value = el.isContentEditable ? (el.textContent || '') : (el.value || '');
        var password = (el.type || '').toLowerCase() === 'password';

        try {
            var b = bridge();
            if (b) b.requestText(id, hint, value, password);
        } catch (e) {
        }

        try {
            el.blur();
        } catch (e) {
        }
    }

    function setNativeValue(el, value) {
        var prototype = el.tagName && el.tagName.toLowerCase() === 'textarea'
            ? window.HTMLTextAreaElement.prototype
            : window.HTMLInputElement.prototype;
        var descriptor = Object.getOwnPropertyDescriptor(prototype, 'value');
        if (descriptor && descriptor.set) {
            descriptor.set.call(el, value);
        } else {
            el.value = value;
        }
    }

    function setText(id, value) {
        var el = textTargets[id];
        if (!el) {
            log('setText: unknown target ' + id);
            return;
        }
        try {
            el.focus();
        } catch (e) {
        }
        try {
            if (el.isContentEditable) {
                el.textContent = value;
            } else {
                setNativeValue(el, value);
            }
            ['input', 'change', 'keyup'].forEach(function (type) {
                var ev = new Event(type, { bubbles: true });
                el.dispatchEvent(ev);
            });
        } catch (e) {
            log('setText error: ' + e);
        }
        delete textTargets[id];
    }

    /* ------------------------------------------------------------------ housekeeping */

    /* Auto-advance when a clip ends. */
    function attachEndedHandlers() {
        var videos = selAll('video');
        for (var i = 0; i < videos.length; i++) {
            var v = videos[i];
            if (v.__tikTvHooked) continue;
            v.__tikTvHooked = true;
            v.addEventListener('ended', function () {
                if (cfg.autoplayNext) {
                    window.setTimeout(function () {
                        step(1);
                    }, 250);
                }
            });
        }
    }

    /* Keep cinema mode alive when TikTok re-renders the feed. */
    function keepCinema() {
        if (!cfg.cinema) return;
        var video = mainVideo();
        if (!video) return;
        if (promotedVideo && document.body.contains(promotedVideo) && promotedVideo === video) return;
        applyCinema(true);
    }

    function diag() {
        var video = mainVideo();
        var next = 0;
        var prev = 0;
        NEXT_SELECTORS.forEach(function (s) {
            if (sel(s) && visible(sel(s))) next++;
        });
        PREV_SELECTORS.forEach(function (s) {
            if (sel(s) && visible(sel(s))) prev++;
        });
        var lines = [
            'TikTV script ' + VERSION,
            'page: ' + location.href,
            'videos on page: ' + selAll('video').length,
            'main video: ' + (video
                ? Math.round(video.getBoundingClientRect().width) + 'x' +
                  Math.round(video.getBoundingClientRect().height) +
                  ' ready=' + video.readyState + (video.paused ? ' paused' : ' playing')
                : 'none'),
            'next buttons visible: ' + next,
            'prev buttons visible: ' + prev,
            'scroll container: ' + (findScroller(video) ? 'yes' : 'no'),
            'touch events: ' + ('ontouchstart' in window ? 'yes' : 'no'),
            'cinema mode: ' + (cfg.cinema ? 'on' : 'off'),
            'auto-advance: ' + (cfg.autoplayNext ? 'on' : 'off'),
            'viewport: ' + window.innerWidth + 'x' + window.innerHeight
        ];
        return lines.join('\n');
    }

    function status() {
        var v = mainVideo();
        return JSON.stringify({
            hasVideo: !!v,
            paused: v ? v.paused : true,
            muted: v ? v.muted : false,
            src: srcOf(v),
            cinema: cfg.cinema
        });
    }

    /* ------------------------------------------------------------------ public API */

    window.Tv = {
        __tikTv: true,
        version: VERSION,
        next: function () {
            step(1);
        },
        prev: function () {
            step(-1);
        },
        toggle: toggle,
        play: play,
        pause: pause,
        seek: seek,
        mute: mute,
        scroll: scrollPage,
        cinema: applyCinema,
        setText: setText,
        diag: diag,
        status: status,
        config: function (json) {
            try {
                var parsed = JSON.parse(json);
                for (var key in parsed) {
                    if (Object.prototype.hasOwnProperty.call(parsed, key) && key in cfg) {
                        cfg[key] = parsed[key];
                    }
                }
            } catch (e) {
                log('config error: ' + e);
            }
        }
    };

    document.addEventListener('focusin', onFocusIn, true);

    window.setInterval(function () {
        try {
            attachEndedHandlers();
            keepCinema();
        } catch (e) {
        }
    }, 1200);

    attachEndedHandlers();
    log('tv.js ' + VERSION + ' ready on ' + location.href);
})();
