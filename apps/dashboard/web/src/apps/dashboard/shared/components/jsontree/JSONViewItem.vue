<template>
  <div class="json-view-item" ref="container">
    <!-- Handle Objects and Arrays-->
    <div v-if="data.type === 'object' || data.type === 'array'">
      <button
        @click.stop="toggleOpen"
        class="data-key"
        :aria-expanded="open ? 'true' : 'false'"
      >
        <div :class="classes"></div>
        <span :class="keyClass">{{ data.key }}:</span>
        <span v-if="isFinalElement()" class="info-container">
          <span class="properties">
            {{highlightItem.message}}
          </span>
        </span>

        <!-- <span v-else class="properties">{{ lengthString }}</span> -->
      </button>

      <json-view-item
        v-on:selected="bubbleSelected"
        v-for="child in data.children"
        :key="getKey(child)"
        :data="child"
        v-show='highlightItem ? shouldShow() : open'
        :maxDepth="maxDepth"
        :canSelect="canSelect"
        :highlightItem="highlightItem"
        :highlightItemMap="highlightItemMap"
      />
    </div>
    <!-- Handle Leaf Values -->
    <div
      :class="valueClasses"
      v-on:click="clickEvent(data)"
      @keyup.enter="clickEvent(data)"
      @keyup.space="clickEvent(data)"
      :role="canSelect ? 'button' : undefined"
      :tabindex="canSelect ? '0' : undefined"
      v-if="data.type === 'value'"
      @mouseover="upHere = true" @mouseleave="upHere = false"
    >
      <v-tooltip bottom :disabled="!this.tooltipValue">
        <template v-slot:activator="{on, attrs}">
          <div v-on="on" v-bind="attrs">
            <span class="value-key">{{ data.key }}:</span>
            <span :style="getValueStyle(data.value)">
              {{ dataValue }}
            </span>
          </div>
        </template>
        <span>
          {{this.tooltipValue}}
        </span>
      </v-tooltip>
    </div>
  </div>
</template>

<script>
import Vue  from 'vue';

export default Vue.extend({
  name: 'json-view-item',
  data: function() {
    return {
      open: true,
    };
  },
  mounted () {
    // this.$refs['container'].scrollIntoView({behavior: 'smooth', block: 'center'})
  },
  props: {
    data: {
      required: true,
      type: Object
    },
    maxDepth: {
      type: Number,
      required: false,
      default: 1
    },
    canSelect: {
      type: Boolean,
      required: false,
      default: false
    },
    highlightItem: {
      type: Object,
      required: false
    },
    highlightItemMap: {
      type: Object,
      required: false
    }
  },
  methods: {
    shouldShow() {
      var ret = this.highlightItem ? (("root."+this.highlightItem.path.join(".")).indexOf(this.data.path+this.data.key) == 0) : (this.data.depth < this.maxDepth)
      return ret
    },

    isFinalElement() {
      return this.highlightItem && ("root."+this.highlightItem.path.join(".")) === (this.data.path+this.data.key)
    },

    toggleOpen: function() {
      this.open = !this.open;
    },
    clickEvent: function(data) {
      this.$emit('selected', {
        key: data.key,
        value: data.value,
        path: data.path,
        errorMap: data.errorMap
      });
    },
    bubbleSelected: function(data) {
      this.$emit('selected', data);
    },
    getKey: function(value) {
      if (!isNaN(value.key)) {
        return value.key + ':';
      } else {
        return '"' + value.key + '":';
      }
    },
    getValueStyle: function(value) {
      const type = typeof value;
      switch (type) {
        case 'string':
          return { color: 'var(--vjc-string-color)' };
        case 'number':
          return { color: 'var(--vjc-number-color)' };
        case 'boolean':
          return { color: 'var(--vjc-boolean-color)' };
        case 'object':
          return { color: 'var(--vjc-null-color)' };
        case 'undefined':
          return { color: 'var(--vjc-null-color)' };
        default:
          return { color: 'var(--vjc-valueKey-color)' };
      }
    },
    convertAbsoluteToRelative: function(value) {
      let arr = value.split(".")
      arr = arr.map((v) => {
        if (v.length > 0 && v[0] === "?") {
          return "$"
        } else {
          return v.toLowerCase()
        }
      })

      return arr.join("#")

    }
  },
  computed: {
    classes: function() {
      return {
        'chevron-arrow': true,
        opened: this.open
      };
    },
    tooltipValue() {
      let c = this.highlightItemMap ? this.highlightItemMap[this.convertAbsoluteToRelative(this.data.path)] : null
      return c ? c["value"] : null
    },
    doHighlight() {
      let c = this.highlightItemMap[this.convertAbsoluteToRelative(this.data.path)]
      if (c == null) c = {}
      return  c["highlight"]
    },
    useAsterisk() {
      let c = this.highlightItemMap[this.convertAbsoluteToRelative(this.data.path)]
      if (c == null) c = {}
      return  c["asterisk"]
    },
    valueClasses: function() {
      return {
        'value-key': true,
        'can-select': this.canSelect,
        'sensitive-hightlight-class': this.tooltipValue && this.doHighlight,
        'asterisk-hightlight-class': this.useAsterisk
      };
    },
    keyClass: function () {
      return {
        'key-class' : this.shouldShow() && this.highlightItem
      }
    },
    lengthString: function() {
      if (this.data.errorMap[this.data.path + this.data.key]) {
        return this.data.errorMap[this.data.path + this.data.key].map(x => x.message).join(', ')
      } else {
        let errorMapSize = Object.keys(this.data.errorMap).length
        if (errorMapSize === 0) {
          return '';
        } else {
          return errorMapSize === 1
            ? errorMapSize + ' error'
            : errorMapSize + ' errors';
        }
      }
    },
    dataValue: function() {
      if (typeof this.data.value === 'undefined') {
        return 'undefined';
      }
      return JSON.stringify(this.data.value);
    }
  }
});
</script>

<style lang="scss">
.json-view-item:not(.root-item) {
  margin-left: 15px;
}

.value-key {
  color: var(--vjc-valueKey-color);
  font-weight: 600;
  margin-left: 10px;
  border-radius: 2px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: clip;
  padding: 5px 5px 5px 10px;

  &.can-select {
    cursor: pointer;
    &:hover {
      background-color: var(--rgbaColor11);
      text-overflow: clip;
      white-space: normal;
      word-break: break-all
    }

    &:focus {
      outline: 2px solid var(--vjc-hover-color);
    }
  }
}

.data-key {
  // Button overrides
  font-size: 100%;
  font-family: inherit;
  border: 0;
  padding: 0;
  background-color: transparent;
  width: 100%;

  // Normal styles
  color: var(--vjc-key-color);
  display: flex;
  align-items: center;
  border-radius: 2px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  padding: 5px;

  &:hover {
    background-color: var(--vjc-hover-color);
  }

  &:focus {
    outline: 2px solid var(--vjc-hover-color);
  }

  &::-moz-focus-inner {
    border: 0;
  }

  .properties {
    font-weight: 300;
    opacity: 0.9;
    margin-left: 4px;
    user-select: none;
  }
}

.key-class {
    text-decoration-line: underline;
    text-decoration-style: wavy;
    text-decoration-color: var(--redMetric);
}

.sensitive-hightlight-class {
  background-color: var(--hexColor30);
}


.asterisk-hightlight-class .value-key::before {
    content: "*";
}

.chevron-arrow {
  flex-shrink: 0;
  border-right: 4px solid var(--vjc-arrow-color);
  border-bottom: 4px solid var(--vjc-arrow-color);
  width: var(--vjc-arrow-size);
  height: var(--vjc-arrow-size);
  margin-right: 20px;
  margin-left: 5px;
  transform: rotate(-45deg);

  &.opened {
    margin-top: -3px;
    transform: rotate(45deg);
  }
}

.highlight-property { 
  font-weight: 200;
  color: var(--vjc-key-color);
}
</style>
