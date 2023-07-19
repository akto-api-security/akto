import {create} from "zustand"
import {devtools} from "zustand/middleware"

let testEditorStore = (set)=>({
  
})

testEditorStore = devtools(testEditorStore)
const TestEditorStore = create(testEditorStore)

export default TestEditorStore

