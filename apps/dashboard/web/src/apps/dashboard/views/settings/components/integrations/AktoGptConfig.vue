<template>
	<div>
		<div v-if="loading">
			<spinner></spinner>
		</div>
		<div v-else>
			<simple-table 
				:headers="headers" 
				:items="aktoGptConfig" 
				name="Akto Gpt Configuration" 
				:hideDownloadCSVIcon="true"
				:showName="true"
			>
				<template #item.state="{item}">
                    <v-switch 
					v-model="item.state" 
					@change="updateConfig(item.index)"
					class="switch-state"
					/>
                </template>
			</simple-table>
			<v-btn
				primary dark
				@click="saveAktoGptConfig"
				color="var(--themeColor)"
			> Update
			</v-btn>
		</div>
	</div>
</template>

<script>
import api from "../../api.js"
import SimpleTable from "../../../../shared/components/SimpleTable.vue"
import Spinner from "../../../../shared/components/Spinner.vue"

export default {
	name: "AktoGptConfiguration",
	components: {
		SimpleTable,
		Spinner
	},
	data () {
		return {
			aktoGptConfig: [],
			loading: true,
			headers: [
				{text: '', value: 'color'},
				{text: 'Collection Name', value: 'collectionName'},
				{text: 'Status', value: 'state'}
			],
		}
	},
	methods: {
		saveAktoGptConfig(){
			const updatedItems = this.aktoGptConfig.map(item => ({
				id: item.id,
				state: item.state ? 'ENABLED' : 'DISABLED'
			}));
			api.saveAktoGptConfig(updatedItems).then((resp) => {
				this.transformAktoConfigs(resp['currentState'])
			})
		},
		updateConfig(index){
			this.aktoGptConfig[index].state = !this.aktoGptConfig[index].state
			this.saveAktoGptConfig()
		},
		transformAktoConfigs(configs){
			this.aktoGptConfig = configs
			for (var i = 0; i < configs.length; i++) {
				this.aktoGptConfig[i].state = this.aktoGptConfig[i].state == 'ENABLED' ? true : false
				this.aktoGptConfig[i].index = i
			}
		}
	},
	async mounted() {
		api.fetchAktoGptConfig().then((resp) => {
			this.loading = false
			this.transformAktoConfigs(resp['currentState'])
		})
	}
}
</script>

<style scoped>
.switch-state {
    height: 24px !important;
    display: flex;
    align-items: center;
}
</style>