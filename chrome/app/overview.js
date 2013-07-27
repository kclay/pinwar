(function ($$, w, d) {


    var OverviewView = Backbone.View.extend({


        _findTimeout: (60 * 2) + 5,
        _findWidthRatio: null,
        _findTimePassed: 0,
        _findInterval: null,
        events: {
            "click #find": "find",
            "submit .email form": "invite",
            "click .details a": "switchContext"
        },
        initialize: function () {
            this.$email = this.$(".email input:text");
            this.$invite = this.$("#invite");
            this.$challenger = this.$(".right.stats");
            this.$details = this.$(".details");
            this.$progress = this.$(".progress");
            this._findWidthRatio = 100 / this._findTimeout;


            this.EVENTS.REGISTERED.on(this._onRegistered.bind(this));

            this.EVENTS.ChallengeRequest.on(this._onChallengeRequested.bind(this))
            this.EVENTS.WarAccepted.on(this._onWarAccepted.bind(this));
            this.EVENTS.Countdown.on(this._onCountdown.bind(this));


        },
        _onWarAccepted: function (data) {
            w.clearInterval(this._findInterval);
            this.EVENTS.FEEDBACK(null);
            this.$progress.animate({width: "0%"}, 500, function () {
                $(this).hide();
            })
            this.$el.hide();


        },

        switchContext: function () {

        },
        _onRegistered: function () {
            this.$el.fadeIn("slow");
            this.ws = this.Browser.WebSocket();
        },
        find: function () {
            // if (this.DB.STATE() != this.STATE.FINDING) {
            this.DB.STATE(this.STATE.FINDING);
            this.ws.Find();
            this.EVENTS.FEEDBACK({
                message: "Searching for someone to battle..."
            })
            this.$progress.width("0%").show();
            this._findInterval = w.setInterval(function () {
                this._onFindCountdown();
            }.bind(this), 1000);
            // }

            return false;
            /* var profile = {
             id: "foo", username: "raydawg88", name: "Stoodio - Web - Mobile - UI - UX",
             avatar: "http://media-cache-ec3.pinimg.com/avatars/raydawg88_1337115522_140.jpg",
             rank: 5689,
             stats: {
             wins: 10,
             loses: 5,
             points: 50000
             }
             }
             this.EVENTS.ChallengeAccepted({
             profile: profile
             })*/

        },
        _onFindCountdown: function () {
            this._findTimePassed++;
            this._updateFindProgress();
        },
        _updateFindProgress: function () {
            var width = this._findWidthRatio * this._findTimePassed;
            this.debug("updateFindProgress = ? ", width)
            if (width > 100) {
                width = 100;
            }
            this.$progress.animate({width: width + "%"}, 500);
            if (width == 100) {
                w.clearInterval(this._findInterval);
                this.EVENTS.ERROR({
                    message: $("#templateNoChallengers").html()
                })
            }

        },
        invite: function () {
            var email = $.trim(this.$email.val());

            if (!email) {
                return false;
            }

            this.ws.Invite(email);


            return false;
        },
        _onCountdown: function (data) {
            /***
             * data={
             * passed:Int
             * }
             *
             *
             */


            this._findTimePassed = data.passed;
            this._updateFindProgress()

        },
        _onChallengeRequested: function (data) {
            /**
             * data = {
             * token:String,
             * profile:Profile
             * }
             */
                //$("body").addClass("challengeRequest")
            this.$invite.fadeOut("slow");
            var profile = data.profile;

            var challenger = new Player(profile);
            challenger.bindTo(this.$challenger).update();
            this.$challenger.fadeIn("slow");

            var width = this.$details.outerWidth();
            this.$details.animate({right: -width});


        }

    })

    w.OverviewView = OverviewView;
})($$, window, document);
