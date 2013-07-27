/**
 * Created with IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 3:22 PM
 * To change this template use File | Settings | File Templates.
 */
(function ($$, w, d) {


    d.addEventListener("warEvent", function (e) {

        $$.debug("window.message (?)", e);
        var msg = e.detail;
        if (msg && msg.name == "track") {
            w.Tracker(msg.data);
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

        Repinned: function (id, board_id, images) {

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
                action: "re_pinned",
                id: id,
                board: board_id,
                images: images
            }
        },
        CreateBoard: function (id, name, category, url) {

            return {
                action: "create_board",
                id: id,
                name: name,
                category: category,
                url: url
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
        var action = new Actions.CreateBoard(data.id, data.name, data.category, data.url)
        $$.debug("BoardResource = ? ", action);

        track(action);


    }
    trackers.RepinResouce = function (/** Bundle **/ bundle) {
        var resp = bundle.response;
        var data = resp.resource_response.data;

        var images = [];
        $(data.images).each(function (name) {
            var image = this;
            image.name = name;
            images.push(image);
        })


        var action = new Actions.Repinned(data.id, data.board.id, images);

        track(action);
    }


})($$, window, document);