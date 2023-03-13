<template>
    <div style="padding: 24px; height: 100%" v-if="tag_config_copy">
        <v-container>
            <div style=" display: flex">
                <div class="form-text">
                    Name
                </div>
                <div style="padding-top: 0px">
                    <v-text-field
                        placeholder="Add tag name"
                        flat solo
                        class="form-value"
                        :readonly="tag_config_copy['createNew'] ? false:true"
                        v-model="tag_config_copy.name"
                        :rules="name_rules"
                        hide-details
                    />
                </div>
            </div>

            <div style=" display: flex" v-if="tag_config_copy.creatorId">
                <div class="form-text">
                    Creator
                </div>
                <div style="padding-top: 0px">
                    <v-text-field
                        flat solo class="ma-0 pa-0"
                        readonly
                        hide-details
                        :value= computeUsername
                    />
                </div>
            </div>

            <div style="display: flex" v-if="tag_config_copy.timestamp">
                <div class="form-text">
                    Last Updated
                </div>
                <div style="padding-top: 0px">
                    <v-text-field
                        flat solo class="ma-0 pa-0"
                        readonly
                        hide-details
                        :value= computeLastUpdated
                    />
                </div>
            </div>

          <div v-if="tag_config_copy.id || tag_config_copy.createNew">
            <v-row style="padding: 36px 12px 12px 12px"  >
                    <conditions-table
                    :conditions="tag_config_copy.keyConditions"
                    initial_string="param_name"
                    table_header="URL conditions"
                    />
                </v-row>
            </div>
            <v-row v-if="tag_config_copy.id || tag_config_copy.createNew" style="padding-top: 30px">
                <div style="padding: 12px">
                  <v-btn
                        @click="save"
                        color="var(--themeColor)"
                        class="save-btn"
                        height="40px"
                        width="100px"
                        :loading="saveLoading"
                  >
                    Save
                  </v-btn>
                </div>
            </v-row>
            <review-table v-if="reviewData" :review-data="reviewData"/>
        </v-container>
    </div>
</template>


<script>
import obj from "@/util/obj"
import ConditionsTable from '../data_types/components/ConditionsTable.vue'
import ReviewTable from "@/apps/dashboard/views/settings/components/data_types/components/ReviewTable";
import {mapState} from "vuex";
import func from "@/util/func";
export default {
    name: "TagConfigDetails",
    props: {
    },
    components: {
        ConditionsTable,
        ReviewTable
    },
    data() {
        return {
            tag_config_copy: null,
            saveLoading: false,
            reviewLoading: false,
            name_rules: [
                    value => {
                        if (!value) return "Required"
                        const regex = /^[a-z0-9_]+$/i;
                        if (!value.match(regex)) return "Alphanumeric and underscore characters"
                        return true
                    },
                ],
        }
    },
    methods: {
        save() {
            this.saveLoading = true
            this.$store.dispatch("tag_configs/createTagConfig", {tag_config: this.tag_config_copy, save: true})
                .then((resp) => {
                  this.saveLoading = false
                }).catch((err) => {
                  this.saveLoading = false
                })

        },
        reviewCustomDataType() {
          this.reviewLoading = true
          this.$store.dispatch("tag_configs/createTagConfig", {tag_config: this.tag_config_copy, save: false})
              .then((resp) => {
                this.reviewLoading = false
              })
              .catch((err) => {
                this.reviewLoading = false
              })
        }
    },
    mounted() {
        this.tag_config_copy = this.$store.state.tag_configs.tag_config
    },
    computed: {
      ...mapState('tag_configs', ['tag_config', 'usersMap', 'reviewData', 'current_sample_data_count', 'total_sample_data_count']),
        computeUsername() {
          if (this.tag_config_copy.creatorId) {
              return this.usersMap[this.tag_config_copy.creatorId]
          }
          return null
        },
        computeLastUpdated() {
          let t = this.tag_config_copy.timestamp
          if (t) {
              return func.prettifyEpoch(t)
          }
        },
        computeLoading() {
            if (this.total_sample_data_count === 0) return 0
            let val = Math.round(100*this.current_sample_data_count / this.total_sample_data_count)
            if (val > 100) return 100
            return val
        }
    },
    watch: {
        tag_config: function(newVal, oldVal) {
            this.tag_config_copy = JSON.parse(JSON.stringify(newVal))
            this.$store.state.tag_configs.reviewData = null
        },
    }
}

</script>

<style lang="sass" scoped>
    .sensitive-text
        font-size: 16px
        font-weight: bold
        width: 210px
        align-items: center
        display: flex
        padding: 0px
        padding-left: 12px
        color: var(--themeColorDark)
        &.true
            color: var(--v-redMetric-base)
        &:hover
            cursor: pointer

    .save-btn
        background-color: var(--themeColor) !important
        font-size: 16px
        font-weight: 600
        vertical-align: middle
        border-radius: 4px
        text-transform: none
        letter-spacing: normal
        width: 100%
        height: 48px !important
        margin-bottom: 24px
        color: var(--white)

        &.v-btn--disabled
            opacity: 0.3

    .review-btn
        font-size: 16px
        font-weight: 600
        vertical-align: middle
        border-radius: 4px
        text-transform: none
        letter-spacing: normal
        width: 100%
        height: 48px !important
        margin-bottom: 24px
        color: var(--themeColorDark)


    .form-text
        color: grey
        font-size: 16px
        width: 200px
        align-items: center
        display: flex
    
</style>

<style>
    .v-input, .v-input input, .v-input textarea {
        color:  var(--themeColorDark) !important
    }
</style>