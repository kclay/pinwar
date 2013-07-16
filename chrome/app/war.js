/**
 * Created with IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 12:53 PM
 * To change this template use File | Settings | File Templates.
 */

(function (ctx, w, d) {


    ctx.Platform.onMessage(function (request, sender, sendResponse) {
        if (ctx.War != null) {
            ctx.War.onMessage(request, sender, sendResponse);
        }
    })

    var War = Backbone.Model({


        /**
         * @var Player
         */
        me: null,
        /**
         * @var Player
         */
        opponent: null,
        /**
         *
         * @param me
         * @param opponent
         */
        init: function (me, opponent) {
            this.me = me;
            this.opponent = opponent;
            opponent.fetch();

        },
        onMessage: function (request, sender, sendResponse) {

        }
    })
    var PlayerView = Backbone.View.extend({

        _totalBars: 10,
        _maxValuePerBar: 0,
        username: null,
        userId: null,
        maxPoints: 10000,
        initialize: function () {
            this.$points = this.$(".points")
            this.$bars = this.$(".points div").sort(function (a, b) {
                a = parseInt(a.className.replace("bar", ""), 10);
                b = parseInt(b.className.replace("bar", ""), 10);
                return  a > b ? 1 : -1;
            })


        }



    })

    var Player = Backbone.View.extend({
        defaults: {
            wins: 0,
            loses: 0,
            rank: 0
        },
        urlRoot: ctx.path("/war/player"),

        track: function () {

        }
    })

    ctx.PlayerView = PlayerView;
    ctx.newGame = function () {
        if (ctx.War) {
            ctx.War.destroy()
        }


        return ctx.War = new War();
    }

})($$, window, document);