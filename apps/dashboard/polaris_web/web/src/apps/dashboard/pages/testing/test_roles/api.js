import request from "@/util/request"

const testRolesApi = {
    async fetchTestRoles() {
        const resp = await request({
            url: '/api/fetchTestRoles',
            method: 'post',
            data: {}
        })
        return resp
    },
    async addTestRoles (roleName, andConditions, orConditions) {
        const resp = await request({
            url: '/api/addTestRoles',
            method: 'post',
            data: { roleName, andConditions, orConditions }
        })
        return resp        
    },
    async updateTestRoles (roleName, andConditions, orConditions) {
        const resp = await request({
            url: '/api/updateTestRoles',
            method: 'post',
            data: { roleName, andConditions, orConditions }
        })
        return resp        
    },
}

export default testRolesApi