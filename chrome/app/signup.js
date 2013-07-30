/**
 * Created with IntelliJ IDEA.
 * User: Keyston
 * Date: 7/9/13
 * Time: 9:18 PM
 * To change this template use File | Settings | File Templates.
 */
(function ($$, w, d) {


    var SignupView = Backbone.View.extend({
        events: {
            'submit form': 'validateForm'
        },

        initialize: function () {
            this.$email = this.$(":text");
            var profile = this.profile = $$.Browser.db("profile");

            this.EVENTS.REGISTERED.on(this._onRegistered.bind(this));
        },

        _onRegistered: function () {
            this.$el.fadeOut("slow");
            $("#icon-settings").show();
        },
        check: function () {

            if ($$.qs.challenge) {

                this.EVENTS.CHALLENGE($$.qs.challenge, $$.qs.accept ? true : false);
            } else if (this.DB.REGISTERED()) {
                this.EVENTS.REGISTERED();
            } else if (this.DB.CONFIRMATION()) { // needs confirmation
                if ($$.qs.signup) {
                    this.feedback("Confirming signup...")
                    $$.get("war/confirm/" + $$.qs.signup).done(function (data) {
                            this.delay(function () {

                                this.feedback(data);
                                this.EVENTS.REGISTERED();

                            }, 1000);

                        }.bind(this)).error(function (_, _, error) {
                            this.error(error)
                        }.bind(this))
                }

            }

        },
        validateForm: function ($el) {
            var email = $.trim(this.$email.val());
            if (!email) {
                this.error("Please provide a valid email address");
                return false;
            }

            this.submit(email);
            return false;

        },
        submit: function (email) {
            var info = this.profile;
            info.email = email


            // TODO submit
            $$.post('war/signup', {
                profile: info
            }).done(function (data) {
                    this.DB.CONFIRMATION(true);
                    this.SETTINGS.EMAIL(email);
                    this.feedback(data);
                }.bind(this)).error(function (_, _, error) {
                    this.error(error);
                }.bind(this))
        }



    });

    window.SignupView = SignupView;

})($$, window, document);