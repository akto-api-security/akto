// urlB64ToUint8Array is a magic function that will encode the base64 public key
// to Array buffer which is needed by the subscription option

import request from "@/util/request";

const urlB64ToUint8Array = base64String => {
    const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
    const base64 = (base64String + padding).replace(/\-/g, '+').replace(/_/g, '/')
    const rawData = atob(base64)
    const outputArray = new Uint8Array(rawData.length)
    for (let i = 0; i < rawData.length; ++i) {
        outputArray[i] = rawData.charCodeAt(i)
    }
    return outputArray
}

const registerServiceWorker = async () => {
    const swRegistration = await navigator.serviceWorker.register('/sw.js'); //notice the file name
    return swRegistration;
}

const requestNotificationPermission = async () => {
    const permission = await window.Notification.requestPermission();
    if(permission !== 'granted'){
        throw new Error('Permission not granted for Notification');
    }
}

export default {
    triggerSubscriptionAlert: async () => { //notice I changed main to async function so that I can use await for registerServiceWorker
        const swRegistration = await registerServiceWorker();
        await requestNotificationPermission();
        if (window.Notification.permission === 'granted') {
            const applicationServerKey = urlB64ToUint8Array(
                'BMjkoJ-L23wEQ-IcPtGAMAo7tLK7YYMX69rte8h8E_2bLNHT1fVH4YabKxQtQNDmMBjjXf_nlFPuCotfpZscaBU'
            )
            const options = { applicationServerKey, userVisibleOnly: true }
            swRegistration.pushManager.subscribe(options).then(subscription => {
                var subscriptionData = JSON.parse(JSON.stringify(subscription));
                subscriptionData.userAgent = navigator.userAgent;

                request({
                    url: '/api/saveSubscription',
                    method: 'post',
                    data: {subscription}
                }).then((resp)=>{
                    return resp
                })
            })
        }
    }
}
