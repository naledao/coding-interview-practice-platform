<script setup>
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const defaultLinkOpen = markdown.renderer.rules.link_open || ((tokens, index, options, env, self) => self.renderToken(tokens, index, options))

markdown.renderer.rules.link_open = (tokens, index, options, env, self) => {
  const token = tokens[index]
  const hrefIndex = token.attrIndex('href')
  const href = hrefIndex >= 0 ? token.attrs[hrefIndex][1] : ''

  if (/^https?:\/\//i.test(href)) {
    token.attrSet('target', '_blank')
    token.attrSet('rel', 'noopener noreferrer')
  }

  return defaultLinkOpen(tokens, index, options, env, self)
}

const props = defineProps({
  source: {
    type: String,
    default: '',
  },
})

const renderedMarkdown = computed(() => markdown.render(normalizeMarkdown(props.source || '')))

function normalizeMarkdown(source) {
  return source
    .replace(/```([A-Za-z0-9_+#.-]+)[ \t]+([^`\n][\s\S]*?)```/g, (match, language, code) => {
      const trimmedCode = code.trim()
      return trimmedCode ? `\n\n\`\`\`${language}\n${trimmedCode}\n\`\`\`\n\n` : match
    })
    .replace(/```[ \t]+([^`\n][\s\S]*?)```/g, (match, code) => {
      const trimmedCode = code.trim()
      return trimmedCode ? `\n\n\`\`\`\n${trimmedCode}\n\`\`\`\n\n` : match
    })
}
</script>

<template>
  <div class="markdown-content" v-html="renderedMarkdown"></div>
</template>
