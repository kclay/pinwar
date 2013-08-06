var ws;
var tabs = [];
var tabsById = {};

var ctx = {
    profile: {

    },
    settings: {},
    war: null
}


function toTab(tabId, msg) {
    chrome.tabs.sendMessage(tabId, msg);
}

function db(name) {
    return JSON.parse(window.localStorage[name]).data;
}
function forward(name, save, callback) {

    var last = null;
    if (typeof save == "function") {
        callback = save;
        save = false;
    }
    if (!callback)
        callback = function () {
        };

    function apply(e) {
        var msg = {name: name, data: e}
        callback(e.data);
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


function wrapSound(name) {

    var allow = function () {
        return ctx.settings["game-sounds"];
    }


    var f = function () {
        if (!allow())return;
        if (!f._loaded) {
            var audio = f._audio = new Audio();
            audio.setAttribute("src", chrome.extension.getURL("app/sounds/" + name + ".mp3"));
            audio.load();
            f._loaded = true;
        }
        f._audio.play();

    }

    f._loaded = false;

    return function () {
        f()
    };
}
var Sound = {
    WON: wrapSound("won"),
    //INVITE: wrapSound("invite", true),
    POINTS: {
        ME: wrapSound("point_me"),
        OPPONENT: wrapSound("point_opponent")
    },
    LOST: wrapSound("lost")
}


function tabOk(tab) {
    var url = tab.url;
    return url && url.indexOf("pinterest.com") != -1;
}
function syncTab(tabId) {
    if (ctx.war) {
        toTab(tabId, {
            name: "app:onSync",
            data: {
                war: ctx.war
            }
        })
    }

}
function addTab(tab) {
    if (!tabsById[tab.id]) {
        tabs.push(tab);

        tabsById[tab.id] = true;
        syncTab(tab.id);

    }
}
function removeTab(tab) {
    var id = typeof tab == "number" ? tab : tab.id;
    if (tabsById[id]) {
        tabsById[id] = null;
        delete tabsById[id];
        var len = tabs.length
        var index = -1;
        for (var i = 0; i < len; i++) {

            if (tabs[i].id == id) {
                index = i;
                break;
            }

        }
        if (index > -1)
            tabs.splice(index, 1);
    }
}
chrome.tabs.onUpdated.addListener(function (tabId, changeInfo, tab) {
    var ok = tabOk(tab);
    var added = tabsById[tab.id];
    if (ok && !added) {
        addTab(tab);
    } else if (!ok) {
        removeTab(tab);
    }
})
chrome.tabs.onCreated.addListener(function (tab) {
    if (tabOk(tab))
        addTab(tab);


})
chrome.tabs.onRemoved.addListener(function (tabId) {

    removeTab(tabId);

    if (!tabs.length && ws) {

        ws.close();
        ws = null;
        tabsById = {};


    }
});


function handleOnMessage(data) {

    data = JSON.parse(data);
    var bundle = data.data;

    switch (data.event) {

        case "war_accepted":


            ctx.war = bundle;

            ctx.war.points = {
                me: 0,
                opponent: 0
            }


            break;

        case "points":
            var me = (bundle.profileId == ctx.profile.id);
            var amount = bundle.amount;

            if (me) {
                ctx.war.points.me += amount;
                Sound.POINTS.ME();
            } else {
                ctx.war.points.opponent += amount;
                Sound.POINTS.OPPONENT();
            }

            break;

        case "won":
            var won = (bundle.profileId == ctx.profile.id);
            won ? Sound.WON() : Sound.LOST();
            ctx.war = null;
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
        case "ws:sync":
            for (var i in msg.data) {
                ctx[i] = msg.data[i];
            }
            break;
        case "ws:send":
            ws.send(JSON.stringify(msg.data));

            break;
    }
})

