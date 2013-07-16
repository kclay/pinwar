var LikeUs = {};

var div = document.getElementById("likeus-config");
LikeUs.CONFIG = JSON.parse(div.getAttribute("data-config"));
window.likeus_path = function (path) {
    return window.SERVER + path;
}
window.likeus_tpl = function (name, callback) {
    $.get(likeus_path("template/" + name), callback);
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
LikeUs.Logging = {
    LOG_LEVELS: {
        error: 3,
        warn: 2,
        info: 1,
        debug: 0
    },

    logLevel: LikeUs.CONFIG.DEBUG ? 'debug' : 'error',

    log: function (messageArgs, level) {


        var levels = LikeUs.Logging.LOG_LEVELS;
        if (levels[LikeUs.Logging.logLevel] > levels[level]) return;

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

$.each(LikeUs.Logging.LOG_LEVELS, function (level, value) {
    LikeUs.Logging[level] = function () {
        this.log(arguments, level);
    };
});
function likeus_debug() {
    LikeUs.Logging.log(arguments, "debug")
}

function likeus_error() {
    LikeUs.Logging.log(arguments, "errror")
}
LikeUs.INIT=true;