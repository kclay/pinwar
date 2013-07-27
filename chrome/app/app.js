$body = $("body");
var AppView = Backbone.View.extend({


    signupView: null,
    firstName: null,

    _previousState: null,
    findTimeout: 60 * 2,

    initialize: function () {


        var self = this;
        var info = jQuery.parseJSON(
            $('script').text().match(
                /P\.currentUser\.set\((.+)\);/)[1]);
        var profile = typeof $$.Browser.db("profile") == "object"
        if (!profile) {
            /*  $.ajax({
             url: "/settings",
             dataType: "json",
             async: false,
             success: function (data) {
             self.firstName = data.module.tree.first_name
             // $$.db("firstName", self.firstName);

             }
             });
             */
            $.ajax({
                url: "/" + info.username,
                dataType: "json",
                async: false,
                success: function (data) {
                    var $html = $(data.module.html);
                    var avatar = $html.find('.userProfileImage img').attr("src")
                    var profile = _.findWhere(data.module.tree.children, {name: "UserProfileHeader"}).data
                    profile = {
                        name: profile.full_name,
                        firstName: profile.first_name,
                        username: profile.username,
                        id: profile.id,
                        avatar: avatar
                    }
                    $$.Browser.db("profile", profile);
                }

            })
        }


        $.get(likeus_path("template/app/tpl.html"), function (html) {

            self.loaded(html)


        })

        this.EVENTS.STATE_CHANGED.on(this._onStateChange.bind(this));
    },
    _onStateChange: function (state) {
        //this.DB.STATE(state);
        if (this._previousState) {
            $body.removeClass("state-" + this._previousState);
        }
        $body.addClass("state-" + state);

    },

    loaded: function (html) {
        $(".Header").before(html);
        this.setElement($("#pinwar"));


        var profile = this.profile = $$.Browser.db("profile");

        this.me = new Player(profile);
        this.me.bindTo(this.$("#overview"));

        this.$(".left.profile")
            .find("img.avatar").attr("src", profile.avatar)
            .end()
            .find(".username").text(profile.name)
            .end()


        this.EVENTS.REGISTERED.on(this._onRegistered.bind(this)); // make sure this is done first
        this.EVENTS.CHALLENGE.on(this._onChallenge.bind(this));

        // this.Messages.on(this._onWarAccepted.bind(this));
        this.signupView = new SignupView({el: this.$("#signup")});
        this.overviewView = new OverviewView({el: this.$("#overview")})
        this.feedbackView = new FeedbackView({el: this.$("#feedback")});
        this.warView = new WarView({el: this.$("#war")});

        //this.inviteView = new InviteView({el:this.$("#invite")});


        this.signupView.check();


    },

    ws: function () {
        if (!this._ws) {
            var ws = this._ws = $$.Browser.WebSocket();
            ws.error(this._onWebSocketError.bind(this))
                .message(this._onWebSocketMessage.bind(this))
                .opened(this._onWebSocketOpen.bind(this))
                .closed(this._onWebSocketClose.bind(this));
        }
        return this._ws;
    },
    _onChallenge: function (token, accept) {

        this.ws(); // connect to server

        this._onConnected.push(function (ws) {
            ws.HandleInvite(token, accept);
        });


    },
    _onConnected: [],

    /**
     *
     * @param event either "error" or "feedback"
     * @param data
     * @private
     */

    _onRegistered: function () {
        this.me.fetch()
        var ws = this.ws();


    },
    _onWebSocketClose: function (e) {

    },
    _onWebSocketError: function (e) {

    },
    _onWebSocketOpen: function (e) {
        var ws = this.ws();
        _.each(this._onConnected, function (f) {
            f(ws);
        })
        this._onConnected = [];
    },
    _onWebSocketMessage: function (e) {

        if (e.event == "feedback" || e.event == "error") {
            if (e.event == "feedback") {
                this.EVENTS.FEEDBACK(e.data);
            } else {
                this.EVENTS.ERROR(e.data);
            }
        } else {
            this.EVENTS.MESSAGE(e.event)(e.data);
        }
        this._handleWebsocketMessage(e.event, e.data);
    },
    _handleWebsocketMessage: function (event, data) {

    }




})


window.app = new AppView();
