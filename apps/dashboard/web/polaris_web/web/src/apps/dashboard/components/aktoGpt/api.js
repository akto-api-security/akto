import request from "@/util/request"

const api = {
    ask_ai(data) {
        return request({
            url: '/api/ask_ai',
            method: 'post',
            data: data,
        })
    }
}

export default api