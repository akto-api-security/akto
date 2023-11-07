console.log('service worker msgs')

self.addEventListener('fetch', function(event) {
    // console.log(event.request.url);
});

self.addEventListener('push', function(event) {
    if (event.data) {
        console.log('Push event!! ', event.data.text())
        self.registration.showNotification('Akto', JSON.parse(event.data.text()))
    } else {
        console.log('Push event but no data')
    }
})

self.addEventListener('activate', async () => {
    // This will be called only once when the service worker is activated.
    console.log('service worker activate')
})