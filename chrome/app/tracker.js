/**
 * Created with IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 3:22 PM
 * To change this template use File | Settings | File Templates.
 */
(function ($$, w, d) {


    $$.Browser.onMessage(function (request, sender, sendResponse) {
        if ($$.War != null) {
            w.Tracker(request);
        }
    })
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

        if (trackers[bundle.resource]) {
            trackers[bundle.resource](bundle);
        }
    }

    /**
     *
     * @param bundle
     * @constructor
     */

    var Actions = {

        Pin: {
            Repin: function (id, board_id, images) {

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
                    id: id,
                    board: board_id,
                    images: images
                }
            }
        },
        Board: {
            Create: function (id, name, category, url) {

                return {
                    id: id,
                    name: name,
                    category: category,
                    url: url
                }
            },
            Stats: function (id, pins, followers) {
                return {
                    id: id,
                    pins: pins,
                    followers: followers
                }
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
        var action = new Actions.Board.Create(data.id, data.name, data.category, data.url)

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


        var action = new Actions.Pin.Repin(data.id, data.board.id, images);

        track(action);
    }


})