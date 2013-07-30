var $$ = {};
(function ($$, w, d) {
    var store = window.localStorage;


    chrome.runtime.onMessage.addListener(function (msg) {
        var ws = Browser.WebSocket();
        switch (msg.name) {

            case "ws:onConnected":
                ws.onopen(msg.data);
                break;
            case "ws:onError":
                ws.onerror(msg.data);
                break;
            case "ws:onMessage":
                ws.onmessage(JSON.parse(msg.data.data));

                break;
            case "ws:onClosed":
                ws.onclose(msg.data);
                break;
            case "app:onSync":
                $$.EVENTS.SYNC(msg.data);
                break;
        }
    });
    function send(data) {
        chrome.runtime.sendMessage(data);
    }

    var Browser = {

        WebSocket: function () {

            page = this;
            if (page._webSocket) {
                return page._webSocket;
            }

            var profile = this.db("profile");
            profile.email = "";
            var profileId = profile.id;

            send({name: "ws:connect", url: $$.path("war/" + profileId).replace("http:", "ws:")})

            page._webSocket = {
                queue: null,
                error: function (c) {
                    this.onerror = c;
                    return this;
                },
                opened: function (c) {
                    this.onopen = c;
                    return this;

                },
                closed: function (c) {
                    this.onclose = c;
                    return this;
                },
                message: function (c) {
                    this.onmessage = c;
                    return this;
                },
                send: function (json) {

                    send({
                        name: "ws:send",
                        data: json
                    });

                },
                close: function () {
                    //ws.close();
                },
                event: function (event, data) {
                    data.profileId = profileId;
                    this.send({
                        event: event,
                        data: data
                    })
                },
                Invite: function (email) {
                    this.event("invite", {
                        email: email
                    })
                },
                Find: function () {
                    this.event("find", {
                        timeout: app.findTimeout
                    });
                },
                Confirm: function () {
                    this.WarAction({profileId: profileId})
                },
                ChallengeResponse: function (request, accepted) {
                    this.event("challenge_response", {
                        token: request.token,
                        creatorId: request.profile.id,
                        accepted: accepted
                    })

                },
                HandleInvite: function (token, accept) {
                    this.event("handle_invite", {
                        token: token,
                        accept: accept,
                        profile: profile
                    })
                },
                WarAction: function (action) {
                    this.event("war_action",
                        {
                            war: w.War.id,

                            action: action
                        });

                }

            }

            return page._webSocket;

        },
        onMessage: function (callback) {
            chrome.runtime.onMessage.addListener(callback);

        },

        /**
         *
         * @param key
         * @param [value]
         */
        db: function (key, value) {

            if (value === false) {
                delete store[key];
                return;
            }

            var getter = typeof value == "undefined";
            if (!getter) {
                var value = {
                    data: value
                }
                store[key] = JSON.stringify(value);
            }

            if (getter) {
                var tmp = store[key];
                if (typeof tmp != "undefined" && tmp != "undefined") {
                    return JSON.parse(store[key]).data;
                }
            }


            return false;

        }

    }


    Backbone.View.prototype.Browser = $$.Browser = Browser;


})($$, window, document);