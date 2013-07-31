/**
 * Created with IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 3:22 PM
 * To change this template use File | Settings | File Templates.
 */
(function ($$, w, d) {


    d.addEventListener("warEvent", function (e) {

        if (w.app.warView.active) {
            var msg = e.detail;
            if (msg && msg.name == "track") {
                w.Tracker(msg.data);
            }
        }
    }, false);
    var BoardResponse = {
        resource_response: {
            data: {}
        }
    }
    var ModuleResponse = {
        html: "",
        tree: {
            resource: {},
            name: "",
            children: [],
            deps: [],
            attributes: {},
            data: {},
            options: {}
        }
    }

    function track(action) {
        $$.Browser.WebSocket().WarAction(action);

    }

    var trackers = {}


    w.Tracker = function (/** Bundle **/bundle) {

        $$.debug("Tracker = ? ", bundle);
        if (trackers[bundle.resource]) {
            trackers[bundle.resource](bundle);
        }
    }


    var resolveCategory = function (pin, callback) {
        var onBoardPage = $(".BoardPage").length;
        var onFeedPage = $("h1.feedName") && window.location.href.indexOf("/all/") != -1;
        var boardUrl;

        if (onFeedPage) {
            var parts = window.location.href.split("/")
            return callback(parts[parts.length - 1])
        }
        if (onBoardPage) {

            boardUrl = window.location.href;
        } else {
            boardUrl = $("a[href='/pin/" + pin + "/']").parents(".pinWrapper").find(".pinCredits a:last").attr("href")
        }

        if (boardUrl) {
            $.get(boardUrl, '', function (json) {
                try {
                    var category = json.page_info.meta["pinterestapp:category"];
                    callback(category);
                } catch (e) {
                }

            }, 'json');
        }
    }
    /**
     *
     * @param bundle
     * @constructor
     */

    var warp = function (name, f) {
        var data = f();
        data.action = name;
        return data;
    }
    var Actions = {
        Like: function (id, category) {

            return {
                action: "like",
                id: id,
                category: category
            }
        },
        Repin: function (id, board_id, category, images) {

            /**
             * images = {
                 *      <name> : {
                 *       url : String
                 *       width : Number
                 *       height : Number
                 *
                 *      }
                 */
            return {
                action: "repin",
                id: id,
                category: category,
                boardId: board_id,
                images: images
            }
        },
        Pin: function (id, board_id, images) {

            /**
             * images = {
                 *      <name> : {
                 *       url : String
                 *       width : Number
                 *       height : Number
                 *
                 *      }
                 */
            return {
                action: "pin",
                id: id,
                boardId: board_id,
                images: images
            }
        },
        CreateBoard: function (id, name, category, description, url) {

            return {
                action: "board",
                id: id,
                name: name,
                description: description,
                category: category,
                url: url
            }
        },
        Comment: function (id, pinId, category) {
            return{
                action: "comment",
                id: id,
                pinId: pinId,
                category: category
            }
        }


    }

    trackers.BoardResource = function (/** Bundle **/bundle) {
        /**
         *
         * @var BoardResponse
         */
        var resp = bundle.response;
        var data = resp.resource_response.data;
        if (data) {
            var action = new Actions.CreateBoard(data.id, data.name, data.category, data.description, data.url)
            $$.debug("BoardResource = ? ", action);

            track(action);

        }

    }

    var withImages = function (data) {
        var images = [];
        _.each(data.images, function (value, key) {

            value.name = key;
            images.push(value);
        })
        return images;
    }
    trackers.RepinResource = function (/** Bundle **/ bundle) {
        var resp = bundle.response;
        var pinId = resp.resource.options.pin_id;
        var data = resp.resource_response.data;

        resolveCategory(pinId, function (category) {
            var action = new Actions.Repin(data.id, data.board.id, category, withImages(data));

            track(action);

        })

    }


    trackers.PinResource = function (/** Bundle **/ bundle) {
        var resp = bundle.response;
        var data = resp.resource_response.data;

        var action = new Actions.Pin(data.id, data.board.id, withImages(data));
        track(action);

    }

    trackers.PinLikeResource = function (/** Bundle **/ bundle) {
        var resp = bundle.response;
        var data = resp.resource.options;
        var pinId = data.pin_id;
        resolveCategory(pinId, function (category) {
            var action = new Actions.Like(pinId, category);
            track(action);
        });
    }

    trackers.PinCommentResource = function (/** Bundle **/ bundle) {
        var resp = bundle.response;
        var data = resp.resource_response.data;

        var pinId = resp.resource.options.pin_id;
        resolveCategory(pinId, function (category) {
            var action = new Actions.Comment(data.id, pinId, category)
            track(action);
        })
    }


})($$, window, document);