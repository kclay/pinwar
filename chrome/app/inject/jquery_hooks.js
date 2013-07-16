(function ($) {
    var classFuncs = {add: $.fn.addClass, remove: $.fn.removeClass}
    var classToListenFor = {};
    chrome.extension.onMessage.addListener(function (message, sender, sendResponse) {
        console.log(message);
        if (message.name == "class_listener") {

            if (message.add) {
                classToListenFor[message.cls] = true;
            }
        }
    })

    function dispatch(el, added) {
        var $this = $(el),
            classes = $this.attr("class").split(" ")
        var cls;
        for (var i in classes) {
            cls = $.trim(classes[i]);
            if (classToListenFor[cls]) {
                chrome.extension.sendMessage({
                    name: "class_changed",
                    cls: cls,
                    id: $this.attr("id"),
                    added: added
                })

            }
        }
    }

    $.fn.addClass = function () {
        classFuncs.add.apply(this, arguments);
        dispatch(this, true);


        return $(this);
    }
    $.fn.removeClass = function () {
        classFuncs.remove.apply(this, arguments);
        dispatch(this, false);
        return $(this);
    }
})(jQuery);