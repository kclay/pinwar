/**
 * Created with IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 12:53 PM
 * To change this template use File | Settings | File Templates.
 */

(function (ctx, w, d) {


    var selfProfileId;
    var WarView = Backbone.View.extend({


        active: false,
        war: null,
        initialize: function () {
            this.vs = this.$(".vs img").attr("src");
            this.$stats = this.$(".left.profile .stats").clone();
            this.EVENTS.WarAccepted.on(this._onWarAccepted.bind(this));
            this.EVENTS.Won.on(this._onWon.bind(this));
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
        _onWon: function (data) {
            /**
             * data = {
             * profileId:String
             * }
             */
            var won = (data.profileId == this.me.profileId);

            var image = this.vs.replace("vs.png", won ? "trophy.png" : "garbage.png");


            this.$(".vs img").src(image);


            var $right = this.$(".right.profile");
            var gif = $$.path(["assets/images/", won ? "won" : "lose", _.random(1, 10), ".gif"].join(""));
            $right.find("#end-game")
                .find(".label").text(won ? "You Won" : "You Lost").end()
                .find(".message").text(won ? "Oh yeah! Oh Yeah! Oh Yeah!" : "This is no bueno.  Step it up.").end()
                .find(".details").text().end().end()
                .find(".avatar").src(gif).end()


            won ? Sound.WON() : Sound.LOST();
            // won ? Sound.WON() : Sound.LOST();

            //me ? this.me.won() : this.opponent.lose();
        },

        _onPoints: $$.delayMaybe(function (data) {
            /**
             * data = {
             *  profileId:String,
             *  amount:Int,
             *  context:Action={
             *  name:String
             * }
             */

            var me = (data.profileId == this.me.profileId);

            var classTypeAdd = me ? "me" : "opponent";
            var classTypeRemove = me ? "opponent" : "me";


            this.$points.text("+" + data.amount);
            this.$display.text(this.toDisplayName(data.name));
            w.clearTimeout(this.clearTimeoutId);


            me ? this.me.add(data.amount) && Sound.POINTS.ME() : this.opponent.add(data.amount) && Sound.POINTS.OPPONENT();


            if (me)
                this._addPowerUps(data.context.powerUps);


            $("body").addClass("point " + classTypeAdd).removeClass(classTypeRemove);
            this.clearTimeoutId = w.setTimeout(function () {
                $("body").removeClass("point me opponent");
            }, 5 * 1000);


        }),
        _addPowerUps: function (powerUps) {
            if (!powerUps || !powerUps.length)return;
            var up = powerUps[0]
            var msg;
            switch (up.name) {
                case "description":
                    msg = "Great Job...you just got " + up.amount + " extra points cause you added " + up.data + " to your description";
                    break;

            }
            if (msg) {
                this.EVENTS.FEEDBACK({
                    message: msg
                })
            }
        },
        /**
         * Translates an PointContext name to its display name
         * @param name
         * @returns {string}
         */
        toDisplayName: function (name) {
            switch (name) {
                case "pin":
                    return "Created Pin";
                case "repin":
                    return "Repinned";
                case "board":
                    return "Created Board";
                case "like":
                    return "Liked";
                case "comment":
                    return "New Comment";
            }
        },

        _onWarAccepted: function (data) {
            this.$el.show();
            this.active = true;

            var war = w.War = data.war;


            this.$(".category").text(war.rules.category);
            var points = (data.points || {});
            this.me = new PlayerView({
                el: this.$(".left.profile"),
                player: w.app.me,
                points: points.me || 0,
                maxPoints: war.rules.points,
                progress: this.$(".progress.me")
            })
            this.opponent = new PlayerView({
                el: this.$(".right.profile"),
                player: new Player((this.me.profileId == data.creator.id) ? data.opponent : data.creator),
                points: points.opponent || 0,
                maxPoints: war.rules.points,
                progress: this.$(".progress.opponent")
            })
            // restore vs logo in case we updated from win/lose
            this.$(".vs img").attr("src", this.vs);
            // restore just in case we updated from win/lose


            this.EVENTS.FEEDBACK({
                message: "Let the game begin!!!"
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
        _maxPoints: 10000,
        player: null,
        _points: 0,
        initialize: function (options) {
            this.player = options.player;

            this.$stats = this.$(".stats span");
            this.player.bindTo(this.$el);
            this.profileId = this.player.get("id");
            this.$points = this.$(".points")
            this.$bars = this.$(".points div img");
            this._totalBars = this.$bars.length;
            this._maxPoints = options.maxPoints;


            this._maxValuePerBar = Math.ceil(options.maxPoints / this._totalBars);
            this.add(options.points);

        },
        won: function () {
            $("body").addClass("won");
        },
        lost: function () {
            $("body").addClass("lose");
        },
        add: function (points) {

            clearInterval(this._interval);
            var before = this._points;
            var after = this._points += points;


            var diff = after - before;

            if (!diff)return this;
            var step = Math.ceil(500 / diff);
            this._interval = setInterval(function () {

                before += step;
                this.$stats.text("" + before);
                if (before >= after) {
                    clearInterval(this._interval);
                }
            }.bind(this), 1);
            this.update(this._points)

            return this;
        },
        update: function (points) {


            var barValue = this._maxValuePerBar;
            var step = 100 / barValue;
            var $bars = this.$bars;
            return _.chain(_.range(this._totalBars))
                .map(function () {
                    if (points >= barValue) {
                        points -= barValue;
                        return barValue;
                    } else {
                        var value = points;
                        points = 0;
                        return value;
                    }
                }).map(function (value) {
                    return step * value;
                }).each(function (value, index) {
                    $bars.eq(index).delay(index * 200).animate({height: value + "%"}, "slow");

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