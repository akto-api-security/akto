<template>
    <div>
        <div v-if="loading">Loading....</div>
        <div v-if="!loading" class="d-flex lb_dropdown">
            <div v-if="!loading && hasRequiredAccess">
                <v-select v-model="selectedLBs" :items="availableLBs" item-text="resourceName" item-value="resourceId"
                    label="Select LBs" return-object multiple>
                    <template v-slot:selection="{ item, index }">
                        <v-chip v-if="index === 0">
                            <span>{{ item.resourceName }}</span>
                        </v-chip>
                        <span v-if="index === 1" class="grey--text text-caption">
                            (+{{ selectedLBs.length - 1 }} others)
                        </span>
                    </template>
                </v-select>
                <v-btn primary dark color="#6200EA" @click="saveLBs">
                    Update LBs
                </v-btn>
            </div>
        </div>
    </div>
</template>


<script>
import { obj } from 'duplexify';
import api from '../api'
export default {
    name: 'LoadBalancers',
    data() {
        return {
            loading: true,
            hasRequiredAccess: false,
            availableLBs: [],
            selectedLBs: [],
            existingSelectedLBs: [],
        }
    },
    mounted() {
        this.fetchLBs();
    },
    methods: {
        fetchLBs() {
            api.fetchLBs().then((resp) => {
                this.loading = false
                this.hasRequiredAccess = resp.dashboardHasNecessaryRole
                if (!this.hasRequiredAccess) {
                    alert('Please give relevant access to the EC2 instance')
                }
                this.availableLBs = resp.availableLBs;
                this.selectedLBs = resp.selectedLBs;
                this.existingSelectedLBs = resp.selectedLBs;
            })
        },
        saveLBs() {
            api.saveLBs(this.selectedLBs).then((resp) => {
                this.availableLBs = resp.availableLBs;
                this.selectedLBs = resp.selectedLBs;
                this.existingSelectedLBs = resp.selectedLBs;
                let intervalId = null;
                console.log(resp);
                debugger;
                if (resp.isFirstSetup) {
                    console.log('Tracking stack creation status....')
                    intervalId = setInterval(async () => {
                        api.fetchStackCreationStatus().then((resp) => {
                            console.log(resp.stackStatus);
                            if (resp.stackStatus == 'CREATE_IN_PROGRESS') {
                                console.log("Stack is being created");
                            }
                            else if (resp.stackStatus == 'CREATE_COMPLETE') {
                                console.log("Stack created successfully, stopping further calls to status api");
                                clearInterval(intervalId);
                            }
                            else {
                                console.log('Something went wrong here, removing calls to status api');
                                clearInterval(intervalId);
                            }
                        }
                        )
                    }, 5000)
                }
            })
        },
        areListsEqual() {
            if (this.selectedLBs.length != this.existingSelectedLBs.length) {
                return false;
            }
            let obj = {};
            this.selectedLBs.forEach((lb) => obj[lb.resourceName] = true);
            this.existingSelectedLBs.forEach((lb) => {
                if (obj[lb.resourceName] == undefined) {
                    return false;
                }
            })
            return true;

        }
    }
}
</script>


<style lang="sass" scoped>
.lb_dropdown
    max-width: 400px
    .v-select__selections
        padding: 0px !important     
    .v-input__slot
        min-height: 32px !important
</style>
