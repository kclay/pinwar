/**
 * Created with IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 12:53 PM
 * To change this template use File | Settings | File Templates.
 */

(function (ctx, w, d) {


    var War = Backbone.Model.extend({


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
            this.ws = $$.Browser.WebSocket();

        },

        track: function (action) {


            this.ws.send(JSON.stringify({
                event: "war_action",
                data: {
                    war: this.get("id"),
                    profileId: this.me.get("id"),
                    action: action
                }
            }))
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

    var Player = Backbone.Model.extend({
        defaults: {
            id: null,
            username: null,
            name: null,
            avatar: null,
            ranking: {
                wins: 0,
                loses: 0,
                points: 0
            }

        },
        initialize: function () {

            this.on("sync", this._onSynced.bind(this))
        },
        bindTo: function ($view) {
            this.$view = $view;
        },
        _onSynced: function (m) {
            var stats = m.get("stats");
            var rank = m.get("rank");
            this.$view
                .find('.wins').text(stats.wins)
                .end()
                .find(".loses").text(stats.loses)
                .end()
                .find(".rank span").text(rank ? rank : "Unknown")

        },
        urlRoot: ctx.path("war/player")

    })

    window.Player = Player;

    window.PlayerView = PlayerView;
    $$.newGame = function () {
        if (ctx.War) {
            ctx.War.destroy()
        }


        return ctx.War = new War();
    }

})($$, window, document);