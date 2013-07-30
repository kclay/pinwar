function sendMessage(bundle) {
    chrome.tabs.query({active: true, currentWindow: true}, function (tabs) {
        lastTabId = tabs[0].id;
        chrome.tabs.sendMessage(lastTabId, bundle);
    });
}
var ws;


var activeWar = null;

var tabs = [];

function toTab(tabId, msg) {
    chrome.tabs.sendMessage(tabId, msg);
}
function forward(name, save) {

    var last = null;

    function apply(e) {
        var msg = {name: name, data: e}
        for (var id in tabs) {
            toTab(tabs[id].id, msg);
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
        if (activeWar) {
            toTab(tab.id, {
                name: "app:onSync",
                data: {
                    war: activeWar
                }
            })
        }
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


var selfProfileId;
function handleOnMessage(data) {


    switch (data.event) {
        case "war_accepted":

            selfProfileId = JSON.parse(window.localStorage["profile"]).data.id;


            activeWar = data;

            activeWar.points = {
                me: 0,
                opponent: 0
            }


            break;

        case "war_action":
            if (data.profileId == selfProfileId) {
                activeWar.points.me += data.points;
            } else {
                activeWar.points.opponent += data.points;
            }

            break;


    }
}
var debug = true;
chrome.runtime.onMessage.addListener(function (msg) {
    switch (msg.name) {
        case "ws:connect":

            if (!ws) {
                ws = new WebSocket(msg.url);

                ws.onopen = forward("ws:onConnected", true);
                ws.onerror = forward("ws:onError");
                ws.onclose = forward("ws:onClosed");
                ws.onmessage = forward("ws:onMessage", handleOnMessage);
            } else {
                ws.onopen()
            }


            break;
        case "ws:send":
            ws.send(JSON.stringify(msg.data));

            break;
    }
})

