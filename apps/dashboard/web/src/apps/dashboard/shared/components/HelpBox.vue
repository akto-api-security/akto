<template>
    <v-dialog content-class="ma-0" v-model="dialogBoxVariable">
        <div class="help-box ma-0">
            <a-card title="Help" color="var(--rgbaColor2)" subtitle="" icon="$far_question-circle" class="ma-0">
                <template #title-bar>
                    <v-btn plain icon @click="closeDialogBox()" style="margin-left: auto">
                        <v-icon>$fas_times</v-icon>
                    </v-btn>
                </template>
                <div class="pa-4 html-content" v-html="this.content">
                </div>
            </a-card>
        </div>
    </v-dialog>
</template>

<script>
import ACard from '@/apps/dashboard/shared/components/ACard'
import obj from "@/util/obj"

export default {
    name: "HelpBox",
    components: {
        ACard
    },
    data() {
        return {
            dialogBoxVariable: false,
            content: ""
        }
    },
    props: {
        openDetailsDialog: obj.boolR,
        contentPath: obj.strN
    },
    async mounted() {
        await import(/* webpackPrefetch: 0 */`@/apps/dashboard/shared/docs/${this.contentPath}`).then((res) => {
            this.content = res;
        });
    },
    methods: {
        closeDialogBox() {
            this.$emit('closeDialogBox')
        }
    },
    watch: {
        openDetailsDialog(newValue) {
            this.dialogBoxVariable = newValue
            if (!newValue) {
                this.$emit('closeDialogBox')
            }
        },
        dialogBoxVariable(newValue) {
            if (!newValue) {
                this.$emit('closeDialogBox')
            }
        },
        async contentPath(newValue) {
            await import(/* webpackPrefetch: 0 */`@/apps/dashboard/shared/docs/${this.contentPath}`).then(res => this.content = res);
        }
    }
}

</script>

<style scoped>

.help-box {
    width: 50%;
    top: 0;
    position: fixed;
    right: 0;
}

/deep/ .html-content header,
/deep/ .html-content main>div:nth-child(2)>div:last-child,
/deep/ .html-content [data-testid="page.desktopTableOfContents"] {
    display: none;
}

.html-content {
    height: 100vh;
    overflow-y: scroll !important;
}
</style>
