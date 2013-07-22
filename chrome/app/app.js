$body = $("body");
var AppView = Backbone.View.extend({


    signupView: null,
    firstName: null,
    $feedback: null,
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
        $$.Events.on("error", _.bind(this._onError, this));
        $$.Events.on("feedback", _.bind(this._onFeedback, this));
    },
    _onFeedback: function (message, autoClose) {
        this._feedback(message, autoClose);
        $body.addClass("feedback").removeClass("error")

    },
    _feedback: function (message, autoClose) {
        this.$feedback.html(message);
        if (this._feedbackId) {
            clearTimeout(this._feedbackId);
        }
        if (typeof autoClose == autoClose || autoClose) {
            this._feedbackId = setTimeout(function () {
                $body.removeClass("error feedback");
            }, autoClose === true ? 10 * 1000 : autoClose);
        }
    },
    _onError: function (message, autoClose) {
        this._feedback(message);
        $body.addClass("feedback error")

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

        this.WAR_ACCEPTED.on(this._onWarAccepted.bind(this));
        this.signupView = new SignupView({el: this.$("#signup")});
        this.overviewView = new OverviewView({el: this.$("#overview")})
        this.$feedback = this.$("#feedback .inner");
        //this.inviteView = new InviteView({el:this.$("#invite")});


        this.signupView.check();

        var f = this._onHandleFeedback.bind(this);
        this.EVENTS.MESSAGE("feedback").on(f);
        this.EVENTS.MESSAGE("error").on(f);


    },
    _onWarAccepted: function (event, data) {
        var war = data;
        if (ctx.War) {
            ctx.War.destroy()
        }


        return ctx.War = new War();
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
    _onHandleFeedback: function (event, data) {
        var f = event == "feedback" ? this._onFeedback : this._onError
        f.call(this, data.message, true);

    },
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

        this.EVENTS.MESSAGE(e.event)(e.event, e.data);
    }




})


window.app = new AppView();
