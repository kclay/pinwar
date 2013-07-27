/**
 * Created with IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 12:53 PM
 * To change this template use File | Settings | File Templates.
 */

(function (ctx, w, d) {


    var WarView = Backbone.View.extend({

        war: null,
        initialize: function () {
            this.EVENTS.WarAccepted.on(this._onWarAccepted.bind(this));
            this.EVENTS.SYNC.on(this._onSync.bind(this))
            this.EVENTS.Points.on(this._onPoints.bind(this));
            this.$action = this.$(".action");
            this.$points = this.$action.find(".points");
            this.$display = this.$action.find(".bottom");
        },
        _onSync: function (data) {
            if (!data.war)  return;
            this.EVENTS.WarAccepted(data.war);

        },
        _onPoints: function (data) {
            /**
             * data = {
             *  profileId:String,
             *  amount:Int,
             *  context:Action={
             *  name:String
             * }
             */
            var selfProfileId = this.me.player.get("id");
            var self = (data.profileId == selfProfileId);
            var classTypeAdd = self ? "me" : "opponent";
            var classTypeRemove = self ? "opponent" : "me";
            var view = self ? this.me : this.opponent;
            $("body").addClass("point " + classTypeAdd).removeClass(classTypeRemove);

            this.$points.text("+" + data.amount);
            this.$display.text(data.context.action);


        },
        _onWarAccepted: function (data) {
            this.$el.show();
            var profileId = w.app.me.get("id");

            var points = (data.points || {});
            this.me = new PlayerView({
                el: this.$(".left.profile"),
                player: w.app.me,
                points: points || 0,
                progress: this.$(".progress.me")
            })
            this.opponent = new PlayerView({
                el: this.$(".right.profile"),
                player: new Player((profileId == data.war.creatorId) ? data.opponent : data.creator),
                points: points || 0,
                progress: this.$(".progress.opponent")
            })


        }


    })
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
        player: null,
        initialize: function (options) {
            this.player = options.player;
            this.player.bindTo(this.$el);
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
            rank: null,
            stats: {
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
            this.update();
            return this;
        },
        update: function () {
            this._onSynced(this);
            return this;
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
                .end()
                .find("img.avatar").attr("src", m.get("avatar"))
                .end()
                .find(".username").text(m.get("name"))

        },
        urlRoot: ctx.path("war/player")

    })

    window.Player = Player;

    window.PlayerView = PlayerView;
    window.WarView = WarView;


})($$, window, document);