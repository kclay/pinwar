(function ($$, w, d) {

    var $body = $("body");
    var FeedbackView = Backbone.View.extend({

        _currentChallengeRequest: null,
        challengeTimeout: 30,
        events: {
            "click #accept": "acceptChallenge",
            "click #decline": "declineChallenge"
        },

        initialize: function () {

            this.challengeTemplate = $("<div></div>").append($("#templateChallenge").html());

            this.EVENTS.FEEDBACK.on(this._onFeedback.bind(this));
            this.EVENTS.ERROR.on(this._onError.bind(this))
            this.$feedback = this.$(".inner");
            this.EVENTS.ChallengeRequest.on(this._onChallengeRequested.bind(this))
        },

        _onFeedback: function (data) {
            if (this._feedback(data)) {
                $body.addClass("feedback").removeClass("error")
            }

        },
        _feedback: function (data) {
            data = data || {};
            var message = data.message;
            var fade = $body.hasClass("feedback") || $body.hasClass("error");
            if (!message) {
                $body.removeClass("error feedback");

            } else {
                if (fade) {
                    this.$feedback.fadeOut("slow", function () {
                        this.$feedback.html(message).fadeIn("slow");

                    }.bind(this));
                } else {
                    this.$feedback.html(message);
                }

                if (this._feedbackId) {
                    clearTimeout(this._feedbackId);
                }
                if (data.autoClose !== false) {
                    var delay = typeof data.autoClose == "undefined" || data.autoClose == true ? 5000 : data.autoClose;
                    this._feedbackId = setTimeout(function () {
                        $body.removeClass("error feedback");
                    }, delay);
                }
                return true;
            }
        },
        _onError: function (data) {
            if (this._feedback(data)) {
                $body.addClass("feedback error")
            }

        },
        acceptChallenge: function () {

            this._sendChallengeResponse(true);
        },
        declineChallenge: function () {
            this._sendChallengeResponse(false);
        },
        _sendChallengeResponse: function (accept) {
            w.clearInterval(this._countdownInterval);
            if (accept) {
                this._feedback({
                    message: "Waiting for " + this._currentChallengeRequest.profile.name + " to join",
                    autoClose: false
                });
            } else {
                $body.removeClass("feedback");
            }
            var ws = this.Browser.WebSocket();
            ws.ChallengeResponse(this._currentChallengeRequest, accept);

        },
        _onChallengeRequested: function (data) {
            this._currentChallengeRequest = data;

            var profile = data.profile;
            var feedback = this.challengeTemplate.find(".name").text(profile.name).end().html();

            this.EVENTS.FEEDBACK({
                message: feedback,
                autoClose: false
            })
            var timeout = this.challengeTimeout;
            var $countdown = this.$("#countdown");
            var interval = this._countdownInterval = w.setInterval(function () {
                if (timeout < 0) {
                    w.clearInterval(interval);
                    this._sendChallengeResponse(false);
                    return;
                }
                $countdown.text(timeout--);

            }.bind(this), 1000);

        }
    })


    w.FeedbackView = FeedbackView;
})($$, window, document);