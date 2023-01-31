export default {
    numR: {
        type: Number,
        required: true
    },
    strR: {
        type: String,
        required: true
    },
    arrR: {
        type: Array,
        required: true
    },
    objR: {
        type: Object,
        required: true
    },
    boolR: {
        type: Boolean,
        required: true
    },


    numN: {
        type: Number,
        required: false
    },
    objN: {
        type: Object,
        required: false
    },
    strN: {
        type: String,
        required: false
    },
    arrN: {
        type: Array,
        required: false
    },
    boolN: {
        type: Boolean,
        required: false
    },

    overviewChartOpts: {
        legend: {
            enabled: false
        }
    }
}