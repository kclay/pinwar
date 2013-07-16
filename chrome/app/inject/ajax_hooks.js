/*global chrome*/

function ajax_hooks_check() {
    if (typeof LikeUs == "object" && LikeUs.INIT) {
        init_ajax_hooks();

    } else {
        setTimeout(ajax_hooks_check, 1);
    }
}

function init_ajax_hooks() {


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
            /BoardResource/,
            /FeedResource/,
            /UserHomefeedResource/
        ]
        var GET = "GET";
        var POST = "POST";
        $(d).ajaxSend(onAjaxSend);
        // $(d).ajaxSuccess(onAjaxSuccess);


        function onAjaxSend(event, xhr, ajaxOptions) {
            var url = ajaxOptions.url;
            $(toWatch).each(function () {
                if (url.match(this)) {
                    var Bundle = {};
                    Bundle.url = url;

                    Bundle.resource = url.match(/\/([a-z]+Resource)/i)[0];

                    switch (ajaxOptions.type) {
                        case GET:
                            Bundle.request = extract(url);
                            break;
                        case POST:
                            break;

                    }

                    xhr.done(function (json) {

                        Bundle.response = json;

                    })


                }
            })

        }

        /*
         function onAjaxSuccess(event, xhr, ajaxOptions) {
         invokeHandler("after", event, xhr, ajaxOptions);
         }

         function invokeHandler(method, event, xhr, ajaxOptions) {
         var url = ajaxOptions.url;
         var handler;
         for (var i in api_handlers) {
         handler = api_handlers[i];
         if (handler_accepts(handler, url) && typeof handler[method] == "function") {
         handler[method](event, xhr, ajaxOptions);
         }
         }
         }

         function handler_accepts(handler, url) {
         for (var i in handler.urls) {
         if (url.match(handler.urls[i]))return true;
         }
         return false;
         }

         var hook_board_create = {
         urls: [/board\/create/],
         before: function (event, xhr, options) {
         xhr.done(function (json) {
         likeus_debug("board_create finished =?", json);
         alert("board_create")
         track(ACTIONS.board, {
         url: json.url,
         name: json.name
         })

         })
         },
         after: function (event, xhr, options) {

         }


         }
         var hook_pin = {

         }
         var api_handlers = [hook_board_create];
         var track = function (action, data) {
         var c = LikeUs.CONFIG;
         var data = ref ? {
         ref: ref
         } : null
         $.ajax({
         url: likeus_path("api/track/" + [c.BATTLE, c.PROFILE, action].join("/")),
         data: data,

         method: "GET",
         async: false
         })
         }           */
    })(window, document);
}
ajax_hooks_check();





