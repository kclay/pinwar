/*chrome.tabs.onUpdated.addListener(function (tabId, changeInfo) {

 if (changeInfo.status === 'complete') {
 chrome.tabs.executeScript(tabId, {file: "app/inject/support.js"}, function () {

 chrome.tabs.executeScript(tabId, { file: "app/inject/ajax_hooks.js", runAt: "document_end" });
 })

 }
 });*/
