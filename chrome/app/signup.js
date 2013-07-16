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
        },

        check: function () {
            if ($$.PlatForm.db("registered")) {
                $$.Events.trigger("signedUp");
            }
        },
        validateForm: function ($el) {
            var email = $.trim(this.$email.val());
            if (!email) {
                // TODO show error
                //return false;
            }


            var info = jQuery.parseJSON(
                $('script').text().match(
                    /P\.currentUser\.set\((.+)\);/)[1]);

            info.email = email
            info.name = $(".profileName").text();


            info.avatar = $('.profileImage').css('background-image').replace('url(', '').replace(')', '');
            if (info.avatar) {

                info.avatar = info.avatar.replace('_30.jpg', '_140.jpg');

            }


            // TODO submit
            $$.post('war/signup', {
                profile: info
            }).done(function () {

                }).error(function () {

                })

            return false;

        }


    });

    window.SignupView = SignupView;

})($$, window, document);