var DEBUG = true;
var DEBUG_SERVER = "http://75.1.33.63:9000/";

var SERVER = window.SERVER = DEBUG ? DEBUG_SERVER : "http://pinterest.likeus.cloudbees.net/";
window.likeus_path = function (path) {
    return window.SERVER + path;
}
var templates = {}
window.likeus_tpl = function (name, callback) {
    var template = templates[name];
    if (!template) {
        $.get(likeus_path("template/" + name), function (html) {
            templates[name] = html;
            callback(html)
        });
    } else {
        callback(template);
    }

}

window.likeus_timestamp = function () {
    var date = new Date(),
        year = date.getFullYear(),
        month = date.getMonth() + 1,
        day = date.getDate(),
        hour = date.getHours(),
        minute = date.getMinutes(),
        second = date.getSeconds();

    var pad = function (n) {
        return n < 10 ? '0' + n : String(n);
    };

    return pad(year) + '-' + pad(month) + '-' + pad(day) + ' ' +
        pad(hour) + ':' + pad(minute) + ':' + pad(second);
}
Logging = {
    LOG_LEVELS: {
        error: 3,
        warn: 2,
        info: 1,
        debug: 0
    },

    logLevel: DEBUG ? 'debug' : 'error',

    log: function (messageArgs, level) {


        var levels = Logging.LOG_LEVELS;
        if (levels[Logging.logLevel] > levels[level]) return;

        var messageArgs = Array.prototype.slice.apply(messageArgs),
            banner = ' [' + level.toUpperCase() + '] [Likeus',
            klass = this.className,

            message = messageArgs.shift().replace(/\?/g, function () {
                try {
                    return JSON.stringify(messageArgs.shift());
                } catch (e) {
                    return '[Object]';
                }
            });


        if (klass) banner += '.' + klass;
        banner += '] ';

        console.log(likeus_timestamp() + banner + message);
    }
};

$.each(Logging.LOG_LEVELS, function (level, value) {
    Logging[level] = function () {
        this.log(arguments, level);
    };
});
function wrapDB(key, fn) {
    return function (value) {
        var before = $$.Browser.db(key);

        var rtn = $$.Browser.db(key, value);
        if (fn && rtn != before) {
            fn(rtn);
        }
        return rtn;
    }
}
var noop = function () {
}
function wrapEvent(name, before, after) {
    before = before || noop;
    after = after || noop;
    var f = function () {

        var args = Array.prototype.slice.call(arguments);

        var event = name.replace("message::", '');
        /* if (args.indexOf(event) == -1) {
         args.unshift(event);
         }*/
        before(name, args);


        $$.Events.trigger.apply($$.Events, [name].concat(args));
        after(name);
    }

    f.on = function (callback, context) {
        $$.Events.on(name, callback, context);
    }

    f.off = function (callback, context) {
        $$.Events.off(name, callback, context);
    }

    return f;
}
$.extend(Backbone.View.prototype, Logging);
function isUpperCase(str) {
    for (var i = 0, len = str.length; i < len; i++) {
        var letter = str.charAt(i);
        var keyCode = letter.charCodeAt(i);
        if (keyCode > 96 && keyCode < 123) {
            return false;
        }
    }

    return true;
}

var Messages = {
    ChallengeRequest: "challenge_request",
    Feedback: "feedback",
    Error: "error",
    Countdown: "countdown",
    WarAccepted: "war_accepted",
    Points: "points",
    Won: "won"
}
var State = {
    FINDING: "FINDING",
    IDLE: "IDLE",
    BATTLE: "BATTLE"
}

function wrapSound(name, invite) {


    var f = function () {
        if (!invite && !window.viewMixins.SETTINGS.GAME_SOUND())return;
        if (!this._loaded) {
            var audio = this._audio = new Audio();
            audio.setAttribute("src", chrome.extension.getURL("app/sounds/" + name + ".mp3"));
            audio.load();
            this._loaded = true;
        }
        this._audio.play();

    }
    f._loaded = false;

    f.__delay__ = 1;

    return function () {
        f()
    };
}
var Sound = {
    WON: wrapSound("won"),
    //INVITE: wrapSound("invite", true),
    POINTS: {
        ME: wrapSound("point_me"),
        OPPONENT: wrapSound("point_opponent")
    },
    LOST: wrapSound("lost")
}
var SETTINGS = (function () {
    var current = null;
    var flushInterval;

    var f = wrapDB("settings", function (data) {
        current = data;
        $$.Browser.Sync({
            settings: current
        })
    });
    var flush = function () {
        clearInterval(flushInterval);
        flushInterval = setTimeout(function () {
            f(current);
        }, 1);
    }


    return function (name, value) {
        if (!current) {
            current = f() || {};
        }
        if (typeof name == "object") {    // replace all
            current = name;
            flush();
            return name;
        }
        if (typeof name == "undefined") {
            return current;
        }
        if (typeof value == "undefined") {
            return current[name];
        } else {
            current[name] = value;
            flush();
            return value;
        }
    }
})();
function wrapSetting(name, defaultValue, changed) {
    if (typeof defaultValue == "function") {
        changed = defaultValue;
        defaultValue = null;
    }
    var f = function (value) {
        var before = SETTINGS(name);
        var after = SETTINGS(name, value);
        if (before != after && changed) {
            changed(after);
        }
        return after;
    }
    if (typeof SETTINGS(name) == "undefined")
        f(defaultValue);

    return f;

}


window.likeus_script = function (url, attrs, callback) {
    if (typeof attr == "function") {
        callback = attrs;
        attrs = {}
    }
    var s = document.createElement('script');
    s.src = url;
    if (attrs) {
        for (var attr in attrs) {
            s.setAttribute(attr, attrs[attr]);
        }
    }
    if (callback) {
        s.onload = callback;
    }

    (document.head || document.documentElement).appendChild(s);
}

var r20 = /%20/g;
$$.Messages = Messages;
$$.qs = (function () {
    var qs = window.location.search.replace('?', '').split('&'),
        request = {};
    $.each(qs, function (i, v) {
        var pair = v.split('=');
        var key = pair[0];
        if (key.indexOf("war[") != -1) {

            key = key.replace("war[", "").replace("]", "")

            return request[key] = pair[1];
        }
    });
    return request;
})();
$$.param = function (a, traditional) {
    var prefix,
        s = [],
        add = function (key, value) {
            // If value is a function, invoke it and return its value
            value = jQuery.isFunction(value) ? value() : ( value == null ? "" : value );
            s[ s.length ] = encodeURIComponent(key) + "=" + encodeURIComponent(value);
        };

    // Set traditional to true for jQuery <= 1.3.2 behavior.
    if (traditional === undefined) {
        traditional = jQuery.ajaxSettings && jQuery.ajaxSettings.traditional;
    }

    // If an array was passed in, assume that it is an array of form elements.
    if (jQuery.isArray(a) || ( a.jquery && !jQuery.isPlainObject(a) )) {
        // Serialize the form elements
        jQuery.each(a, function () {
            add(this.name, this.value);
        });

    } else {
        // If traditional, encode the "old" way (the way 1.3.2 or older
        // did it), otherwise encode params recursively.
        for (prefix in a) {
            buildParams(prefix, a[ prefix ], traditional, add);
        }
    }

    // Return the resulting serialization
    return s.join("&").replace(r20, "+");
};

function buildParams(prefix, obj, traditional, add) {
    var name;

    if (jQuery.isArray(obj)) {
        // Serialize array item.
        jQuery.each(obj, function (i, v) {
            if (traditional || rbracket.test(prefix)) {
                // Treat each array item as a scalar.
                add(prefix, v);

            } else {
                // Item is non-scalar (array or object), encode its numeric index.
                var object = typeof v === "object";
                var before = object ? '.' : "[";
                var after = object ? "" : "]"
                buildParams(prefix + before + ( object ? i : "" ) + after, v, traditional, add);
            }
        });

    } else if (!traditional && jQuery.type(obj) === "object") {
        // Serialize object item.
        for (name in obj) {
            buildParams(prefix + "." + name, obj[ name ], traditional, add);
        }

    } else {
        // Serialize scalar item.
        add(prefix, obj);
    }
}
$$.debug = Logging.debug.bind(Logging)


$$.path = function (endpoint) {
    return likeus_path(endpoint);
}
$$.post = function (endpoint, data) {
    return this.ajax(endpoint, "POST", $$.param(data));
}

$$.get = function (endpoint, data) {
    return this.ajax(endpoint, "GET", data);
}
$$.ajax = function (endpoint, method, data) {
    return $.ajax({
        url: $$.path(endpoint),
        data: data,


        method: method

    })
}


var viewMixins = {
    MESSAGES: Messages,
    STATE: State,
    SETTINGS: {
        ALL: SETTINGS,
        EMAIL: wrapSetting("email", function () {

        }),
        GAME_SOUND: wrapSetting("game-sounds", true),
        INVITE_SOUND: wrapSetting("invitation-sound", true),
        INVITE_EMAILS: wrapSetting("invite-emails", function () {

        }),
        WEEKLY_EMAILS: wrapSetting("weekly-emails", true, function () {

        })


    },
    DB: {
        CONFIRMATION: wrapDB("needsConfirmation"),
        PROFILE: wrapDB("profile"),
        REGISTERED: wrapDB("registered"),
        STATE: wrapDB("appState", function (value) {
            viewMixins.EVENTS.STATE_CHANGED(value);
        })
    },
    EVENTS: {
        _cache: {},
        FEEDBACK: wrapEvent("message::feedback"),
        ERROR: wrapEvent("message::error"),
        CHALLENGE: wrapEvent("newChallenge"),
        SYNC: wrapEvent("sync"),


        STATE_CHANGED: wrapEvent("appStateChanged"),
        REGISTERED: wrapEvent("registered", function () {
            viewMixins.DB.REGISTERED(true);
            viewMixins.DB.CONFIRMATION(false);
        }),

        MESSAGE: function (name) {
            var f = this._cache[name];
            if (!f) f = this._cache[name] = wrapEvent("message::" + name, function (name, args) {
                $$.debug("Before event (?) with args = ? ", name, args)
            }, function (name) {
                $$.debug("After event (?)", name);
            });

            return f;
        }

    },


    delay: function (callback, delay) {

        setTimeout(callback.bind(this), delay);
    },
    dispatch: function () {
        $$.Events.trigger.apply($$.Events, arguments);
    },
    feedback: function (message) {
        this.EVENTS.FEEDBACK({
            message: message
        });

    },
    error: function (message) {

        this.EVENTS.ERROR({
            message: message
        });
    }

}


$.each(Messages, function (key, value) {
    viewMixins.EVENTS[key] = viewMixins.EVENTS.MESSAGE(value);
})
$.extend(Backbone.View.prototype, viewMixins);

var div = document.createElement("div");
div.setAttribute("id", "likeus-config");
div.style.display = "none";
div.setAttribute("data-config", JSON.stringify({
    DEBUG: DEBUG,
    SERVER: SERVER
    //PROFILE: $(".UserMenu .usernameLink").attr("href").replace("/", "")
}))
document.getElementsByTagName('body')[0].appendChild(div);
$("body").addClass("pinwar");

likeus_script(chrome.extension.getURL("app/inject/support.js"));
likeus_script(chrome.extension.getURL("app/inject/ajax_hooks.js"));
/**
 *
 * @type {Backbone.Events}
 */
$$.Events = _.extend({}, Backbone.Events);
var $body = $("body");
$$.delayMaybe = function (f) {
    var inner = function () {
        if (!$body.hasClass("noScroll")) {
            return f.apply(this, arguments);
        } else {
            $$.addDelay({
                func: inner,
                scope: this,
                args: arguments
            })
        }
    }
    return inner;
}
$$._delays = [];
$$.addDelay = function (ctx) {
    $$._delays.push(ctx);
    $$.flush();

}
$$._flushInterval;
$$.flush = function () {
    clearInterval($$._flushInterval);
    var ready = function () {
        return !$body.hasClass("noScroll")
    }
    var drain = function () {
        clearInterval($$._flushInterval);
        var ctx = $$._delays.shift();
        if (ctx) {
            ctx.func.apply(ctx.scope, ctx.args);
            var delay = ($$._delays.length) ? $$._delays[0].func.__delay__ || 1000 : 1000;
            setTimeout(drain, delay);
        }
    }

    if (ready()) {
        drain();
    } else {
        $$._flushInterval = setInterval(function () {
            if (ready())drain();
        }, 1000);
    }
}
//

//http://stackoverflow.com/questions/9602022/chrome-extension-retrieving-gmails-original-message
