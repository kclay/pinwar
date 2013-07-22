(function ($$, w, d) {


    var OverviewView = Backbone.View.extend({

        events: {
            "click #find": "find",
            "submit .email form": "invite"
        },
        initialize: function () {
            this.$email = this.$(".email input:text");
            this.EVENTS.REGISTERED.on(this._onRegistered.bind(this))
        },
        _onRegistered: function () {
            this.$el.fadeIn("slow");
            this.ws = this.Browser.WebSocket();
        },
        find: function () {
            this.ws.Find();
        },
        invite: function ($el) {
            var email = $.trim(this.$email.val());

            if (!email) {
                return false;
            }

            this.ws.Invite(email);


            return false;
        }

    })

    w.OverviewView = OverviewView;
})($$, window, document);
