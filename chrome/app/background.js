function sendMessage(bundle) {
    chrome.tabs.query({active: true, currentWindow: true}, function (tabs) {
        lastTabId = tabs[0].id;
        chrome.tabs.sendMessage(lastTabId, bundle);
    });
}
var ws;


var tabs = [];
function forward(name, save) {

    var last = null;

    function apply(e) {
        var msg = {name: name, data: e}
        for (var id in tabs) {
            chrome.tabs.sendMessage(tabs[id].id, msg);
        }
    }

    return function (e) {
        if (save && e) {
            last = e;
        }
        if (last && !e) {
            e = last;
        }
        if (!tabs.length) {
            chrome.tabs.query({
                url: "http://pinterest.com/*"
            }, function (tbs) {
                tabs = tbs;
                apply(e);
            })
        } else {
            apply(e);
        }

    }
}


chrome.tabs.onCreated.addListener(function (tab) {
    if (tab.url.indexOf("pinterest.com") != -1) {
        tabs.push(tab);
    }

})
chrome.tabs.onRemoved.addListener(function (tabId) {
    var index = -1;
    var tbs = tabs;
    var total = tabs.length;
    for (var i = 0; i < total; i++) {
        if (tabId == tabs[i].id) {
            index = i;
            break;
        }
    }
    if (index != -1)
        tabs.splice(index, 1);

    if (!tbs.length && ws) {

        ws.close();
        ws = null;
    }
});


chrome.runtime.onMessage.addListener(function (msg) {
    switch (msg.name) {
        case "ws:connect":
            if (!ws) {
                ws = new WebSocket(msg.url);

                ws.onopen = forward("ws:onConnected", true);
                ws.onerror = forward("ws:onError");
                ws.onclose = forward("ws:onClosed");
                ws.onmessage = forward("ws:onMessage");
            } else {
                ws.onopen()
            }

            break;
        case "ws:send":
            ws.send(JSON.stringify(msg.data));

            break;
    }
})

