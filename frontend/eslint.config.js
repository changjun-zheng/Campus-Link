// CR-103 静态扫描接入：ESLint 9 flat config（前端）。
// 组成：js.recommended（基础） + typescript-eslint recommended（TS 正确性） + eslint-plugin-vue 的 flat/essential（Vue 正确性）。
// 说明：类型声明与构建产物由 unplugin-* / vite 生成，不入检；与项目既有约定冲突的规则在文末「逐条关闭」并注明理由。
import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'

export default [
  {
    // 构建产物、依赖目录，以及 unplugin-auto-import / unplugin-vue-components 自动生成的类型声明
    ignores: ['dist/**', 'node_modules/**', 'auto-imports.d.ts', 'components.d.ts'],
  },

  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/essential'],

  {
    // .vue 的 <script lang="ts"> 需二级解析器；vue-eslint-parser 由其负责，这里接上 TS parser
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: { parser: tseslint.parser },
    },
  },

  {
    // unplugin-auto-import 注入的 Element Plus API 全局（构建期注入、ESLint 不可见）；
    // 来源＝vite.config.ts 的 AutoImport + ElementPlusResolver，声明产物是 gitignore 的 auto-imports.d.ts。
    // 只声明实际出现的那两个（其余 API 未使用），且设为只读——拼写错误仍会被 no-undef 抓住。
    languageOptions: {
      globals: { ElMessage: 'readonly', ElMessageBox: 'readonly' },
    },
  },

  {
    // 与项目既有约定冲突、逐条关闭的规则（理由登记于 CR-103 方案 §7，不整包降级为 warn）
    rules: {
      // 根组件 App.vue 按 Vue 官方脚手架约定即为单词名，项目其余组件均已多词
      'vue/multi-word-component-names': 'off',
    },
  },

  {
    // PostListItem 就地点赞（CR-080 的既定设计：以响应 {active,count} 回填当前行、不整表重拉）
    // 需改写父级传入的列表项对象字段，与 vue/no-mutating-props 冲突；仅对该文件关闭此规则
    files: ['src/components/PostListItem.vue'],
    rules: { 'vue/no-mutating-props': 'off' },
  },
]
