
var AppView = Backbone.View.extend({


    signupView: null,
    initialize: function () {


        var self = this;
        $.get(likeus_path("template/app/tpl.html"), function (html) {

            self.loaded(html)


        })
    },
    loaded: function (html) {
        $(".Header").before(html);
        this.setElement($("#pinwar"));
        this.signupView = new SignupView({el: this.$("#signup")});
        //this.inviteView = new InviteView({el:this.$("#invite")});


        this.signupView.check();
    }



})


window.app = new AppView();
