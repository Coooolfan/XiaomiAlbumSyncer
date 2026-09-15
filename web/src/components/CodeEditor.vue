<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Compartment, EditorState, type Extension } from '@codemirror/state'
import { EditorView, keymap, lineNumbers, placeholder as cmPlaceholder } from '@codemirror/view'
import { defaultKeymap, history, indentWithTab } from '@codemirror/commands'
import {
  bracketMatching,
  defaultHighlightStyle,
  indentOnInput,
  syntaxHighlighting,
} from '@codemirror/language'
import { json } from '@codemirror/lang-json'
import { oneDarkHighlightStyle } from '@codemirror/theme-one-dark'
import { useTheme } from '@/composables/useTheme'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    placeholder?: string
    minHeight?: string
  }>(),
  { modelValue: '', placeholder: '', minHeight: '6rem' },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const { isDark } = useTheme()
const host = ref<HTMLElement>()
let view: EditorView | null = null

const colorScheme = new Compartment()
const placeholderConf = new Compartment()

const lightSurface = EditorView.theme({
  '&': { backgroundColor: 'transparent', color: '#334155' },
  '.cm-content': { caretColor: '#334155' },
  '.cm-gutters': { color: '#94a3b8' },
  '.cm-selectionBackground, &.cm-focused .cm-selectionBackground': {
    backgroundColor: 'rgba(59, 130, 246, 0.15)',
  },
})

const darkSurface = EditorView.theme({
  '&': { backgroundColor: 'transparent', color: '#e2e8f0' },
  '.cm-content': { caretColor: '#e2e8f0' },
  '.cm-gutters': { color: '#64748b' },
  '.cm-selectionBackground, &.cm-focused .cm-selectionBackground': {
    backgroundColor: 'rgba(148, 163, 184, 0.25)',
  },
})

function colorExtensions(dark: boolean): Extension {
  return dark
    ? [darkSurface, syntaxHighlighting(oneDarkHighlightStyle)]
    : [lightSurface, syntaxHighlighting(defaultHighlightStyle)]
}

onMounted(() => {
  if (!host.value) return
  view = new EditorView({
    parent: host.value,
    state: EditorState.create({
      doc: props.modelValue,
      extensions: [
        lineNumbers(),
        history(),
        keymap.of([...defaultKeymap, indentWithTab]),
        indentOnInput(),
        bracketMatching(),
        json(),
        colorScheme.of(colorExtensions(isDark.value)),
        EditorView.theme({
          '&': { fontSize: '12px' },
          '.cm-content': {
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
            padding: '8px 0',
            minHeight: props.minHeight,
          },
          '.cm-line': { padding: '0 8px' },
          '.cm-gutters': { backgroundColor: 'transparent', border: 'none', paddingLeft: '4px' },
          '&.cm-focused': { outline: 'none' },
        }),
        placeholderConf.of(cmPlaceholder(props.placeholder)),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) {
            emit('update:modelValue', update.state.doc.toString())
          }
        }),
      ],
    }),
  })
})

watch(
  () => props.modelValue,
  (value) => {
    if (!view) return
    const current = view.state.doc.toString()
    if (value !== current) {
      view.dispatch({ changes: { from: 0, to: current.length, insert: value } })
    }
  },
)

watch(
  () => props.placeholder,
  (value) => {
    view?.dispatch({ effects: placeholderConf.reconfigure(cmPlaceholder(value)) })
  },
)

watch(isDark, (dark) => {
  view?.dispatch({ effects: colorScheme.reconfigure(colorExtensions(dark)) })
})

onBeforeUnmount(() => {
  view?.destroy()
  view = null
})
</script>

<template>
  <div
    ref="host"
    class="w-full overflow-hidden rounded-md border border-slate-200/80 bg-white/80 transition-colors focus-within:border-slate-400 dark:border-slate-700/70 dark:bg-slate-900/50 dark:focus-within:border-slate-500"
  />
</template>
