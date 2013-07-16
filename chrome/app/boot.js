var DEBUG = true;
var DEBUG_SERVER = "http://localhost:9000/";

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
$.extend(Backbone.View.prototype, Logging);

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
var $$ = {};
var r20 = /%20/g;
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
$$.log = Logging.log;


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
//

//http://stackoverflow.com/questions/9602022/chrome-extension-retrieving-gmails-original-message
