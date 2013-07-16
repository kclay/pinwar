(function ($$, w, d) {
    var store = window.localStorage;
    var PlatForm = {


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
            store[key] = value;

        }

    }


    $$.PlatForm = PlatForm;

})($$, window, document);