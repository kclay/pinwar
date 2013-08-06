/*global chrome*/

function ajax_hooks_check() {
    if (typeof LikeUs == "object" && LikeUs.INIT) {
        init_ajax_hooks();

    } else {
        setTimeout(ajax_hooks_check, 1);
    }
}

function init_ajax_hooks() {


    // TODO watch profile change to update backend
    (function (w, d) {
        var ACTIONS = {
            board: "create_board"
        }
        likeus_debug("ajax_hook loaded");


        var qsExtractor = new RegExp("([^?=&]+)(=([^&]*))?", "g");

        var extract = function (url) {
            var q = {};
            url.replace(qsExtractor, function ($0, $1, $2, $3) {
                if ($1 == "data" && typeof $3 == "string") {
                    $3 = JSON.parse(decodeURIComponent($3));
                }
                q[$1] = decodeURIComponent($3);
            })
            return q;
        }

        var toWatch = [
            "BoardResource/create",
            // /FeedResource/,
            "PinCommentResource/create",
            "PinResource/create",
            "PinLikeResource/create",
            "RepinResource/create"
            // /UserHomefeedResource/
        ]
        var GET = "GET";
        var POST = "POST";
        $(d).ajaxSend(onAjaxSend);
        // $(d).ajaxSuccess(onAjaxSuccess);


        function onAjaxSend(event, xhr, ajaxOptions) {
            var url = ajaxOptions.url;
            $(toWatch).each(function (index, value) {

                if (url.indexOf(value) != -1) {
                    var Bundle = {};
                    Bundle.url = url;

                    Bundle.resource = url.match(/\/([a-z]+Resource)/i)[1];

                    switch (ajaxOptions.type) {
                        case GET:
                            Bundle.request = extract(url);
                            break;
                        case POST:
                            break;

                    }

                    xhr.done(function (json) {

                        Bundle.response = json;
                        var evt = document.createEvent("CustomEvent");
                        evt.initCustomEvent("warEvent", true, true, {name: "track", data: Bundle});
                        document.dispatchEvent(evt);

                    })


                }
            })

        }


    })(window, document);
}
ajax_hooks_check();





