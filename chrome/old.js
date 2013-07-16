
$( document ).ready(function() {

    $('.headerContainer, .headerBackground').css('top', '98px');

    var user_info = null;
    try {
        set_user_info();
    } catch(err) {
        prompt_for_login();
    }


    function set_user_info(callback) {
        // Get the text from all of the <script> elements then find the user data.
        user_info = jQuery.parseJSON(
            $('script').text().match(
                /P\.currentUser\.set\((\{"is_employee".+\})\);/)[1]);

        // Add name to user info.
        user_info['first_name'] = null;
        var first_name = $('.profileName').text();
        if (first_name)
            user_info['first_name'] = first_name;

        // Add icon/image to user info.
        user_info['icon'] = null;
        user_info['image'] = null;
        var icon_url = $('.profileImage').css('background-image').replace('url(','').replace(')','');
        if (icon_url) {
            user_info['icon'] = icon_url;
            user_info['image'] = icon_url.replace('_30.jpg', '_140.jpg');
        }

        // Get the HTML from the users settings page.
        $.ajax({
            url: "/settings/",
            cache: false
        }).done(function(html) {
            // Add the email address to user info.
            user_info['email'] = null;
            var email_matches = html.match(/name="email"\s+value="(.+)">/);
            if (email_matches)
                user_info['email'] = email_matches[1];

            // Add the facebook account to user info.
            user_info['facebook'] = null;
            var facebook_matches = html.match(/facebook\.com\/(.+)</);
            if (facebook_matches)
                user_info['facebook'] = facebook_matches[1];

            // Add the twitter account to user info.
            user_info['twitter'] = null;
            var twitter_matches = html.match(/twitter\.com\/(.+)</);
            if (twitter_matches)
                user_info['twitter'] = twitter_matches[1];

            // Add the gender to user info.
            user_info['gender'] = null;
            var gender_matches = html.match(/name="gender"\s+.+\s+.+checked="checked"\s+value="(.+)">/);
            if (gender_matches)
                user_info['gender'] = gender_matches[1];

            // Get the HTML from the users profile page.
            $.ajax({
                url: "/" + user_info['username'] + "/",
                cache: false
            }).done(function(html) {
                // Add the location to user info.
                user_info['location'] = null;
                var location_matches = html.match(/<span class="locationIcon"><\/span>\s+(.+)\s+</);
                if (location_matches)
                    user_info['location'] = location_matches[1];
    
                // Add the website to user info.
                user_info['website'] = null;
                var website_matches = html.match(/class="website ">\s+(.+)\s+</);
                if (website_matches)
                    user_info['website'] = website_matches[1];
    
                // Add the full name to user info.
                user_info['last_name'] = null;
                var last_name_matches = html.match(/<h2 class="userProfileHeaderName">(.+)</);
                if (last_name_matches)
                    user_info['last_name'] = last_name_matches[1].replace(user_info['first_name'] + ' ', '');

    
                // Add the number of boards to user info.
                user_info['boards'] = null;
                var boards_matches = html.match(/pinterestapp:boards": "(\d+)"/);
                if (boards_matches)
                    user_info['boards'] = boards_matches[1];

                // Add the number of pins to user info.
                user_info['pins'] = null;
                var pins_matches = html.match(/pinterestapp:pins": "(\d+)"/);
                if (pins_matches)
                    user_info['pins'] = pins_matches[1];

                // Add the number of followers to user info.
                user_info['followers'] = null;
                var followers_matches = html.match(/pinterestapp:followers": "(\d+)"/);
                if (followers_matches)
                    user_info['followers'] = followers_matches[1];

                // Add the number of following to user info.
                user_info['following'] = null;
                var following_matches = html.match(/pinterestapp:following": "(\d+)"/);
                if (following_matches)
                    user_info['following'] = following_matches[1];

                // Add the number of likes to user info.
                user_info['likes'] = null;
                var likes_matches = html.match(/<a href="\/.+\/likes\/">\s+(\d+)\sLike/);
                if (likes_matches)
                    user_info['likes'] = likes_matches[1];

                // DEBUG
                // console.log(user_info);
                // $('#pinwar').html('<img src=' + user_info.image + ' width="100px" />');

                validate_user();
            });
        });
    }

    function prompt_for_login() {
        $.ajax({
            url: "http://pinwar.nodejitsu.com/user/prompt/",
            cache: false
        }).done(function(html) {
            var new_inner_html = $(html).filter('#pinwar').html();
            $('#pinwar').html(new_inner_html);
        });
    }

    function validate_user() {
        $.ajax({
            url: "http://pinwar.nodejitsu.com/user/validate/",
            type: "POST",
            data: { user_id: user_info['id'] },
            cache: false
        }).done(function(response) {
            if (response) {
                if (response['id'])
                    display_lobby(response);
                else
                    display_game(response);
            } else {
                register_account();
            }
        });
    }

    function register_account() {
        $.ajax({
            url: "http://pinwar.nodejitsu.com/user/register/",
            type: "POST",
            data: { user_id: user_info['id'], user_info: user_info },
            cache: false
        }).done(function(bool) {
            if (bool)
                display_lobby();
            else
                console.log('Error registering account.');
        });
    }

    function display_lobby(user) {
        $.ajax({
            url: "http://pinwar.nodejitsu.com/user/lobby/",
            type: "POST",
            data: { user_id: user_info['id'] },
            cache: false
        }).done(function(html) {
            var new_inner_html = $(html).filter('#pinwar').html();
            $('#pinwar').html(new_inner_html);
        });
    }



    function display_game(game_id) {
        $.ajax({
            url: "http://pinwar.nodejitsu.com/game/main/",
            type: "POST",
            data: { game_id: game_id },
            cache: false
        }).done(function(html) {
            var new_inner_html = $(html).filter('#pinwar').html();
            $('#pinwar').html(new_inner_html);
        });
    }

    // Create a new game.
    $(document).on('click', '#invite_submit', function() {
        $.ajax({
            url: "http://pinwar.nodejitsu.com/game/create/",
            type: "POST",
            data: { 
                user_id: user_info['id'],
                opponent_username: $('#invite_username').val(),
                topic: $('#invite_topic').val(),
                },
            cache: false
        }).done(function(game_id) {
            display_game(game_id);
        });
    });

    // Like
    $(document).on('click', '.PinLikeButton .buttonText', function() {
        if ($(this).text() == 'Like') {
            alert('test');
        }
    });
});