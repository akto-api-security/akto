<template>
  <div class="action-item-owner" v-if="showName">
    <v-avatar :color="getColor()" :size="24">
      <span class="white--text">{{getInitials()}}</span>
    </v-avatar>
    <span>{{ownerName}}</span>
  </div>
  <div v-else>
    <v-tooltip bottom>
      <template v-slot:activator="{ on, attrs }">
        <v-avatar
          :color="getColor()"
          :size="24"
          v-bind="attrs"
          v-on="on"
        >
          <span class="white--text initials">{{getInitials()}}</span>
        </v-avatar>
      </template>
      <span>{{ownerName}}</span>
    </v-tooltip>
  </div>
</template>

<script>
import obj from "@/util/obj"
import func from "@/util/func";

export default {
  name: "OwnerName",
  props: {
    ownerName: obj.strR,
    ownerId: obj.numR,
    showName: obj.boolR
  },
  methods: {
    getInitials() {
      return func.initials(this.ownerName)
    },
    getColor() {
      var c = func.colors()
      return c[func.hashCode(this.ownerName+this.getInitials()) % c.length]
    }
  }
}
</script>

<style scoped lang="sass">
.action-item-owner
  font-size: 16px
  font-weight: 400
  color: var(--themeColorDark)

.initials
  cursor: default
  font-size: 12px

</style>