module.exports = {
    root: true,
    env: {
        browser: true,
        es2022: true,
        node: true
    },
    extends: [
        'eslint:recommended',
        'plugin:vue/vue3-recommended'
    ],
    parser: 'vue-eslint-parser',
    parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
        parser: '@typescript-eslint/parser'
    },
    rules: {
        // 允许 TypeScript 类型语法和未使用变量
        'no-undef': 'off',
        'no-unused-vars': 'off',
        'vue/no-unused-vars': 'off',
        'vue/multi-word-component-names': 'off',
        'vue/require-default-prop': 'off',
        'vue/no-v-html': 'off',
        // Template layout is formatted by Prettier. Keep ESLint focused on
        // correctness: the vue recommended style rules below otherwise report
        // thousands of non-semantic warnings for valid existing templates.
        'vue/html-indent': 'off',
        'vue/singleline-html-element-content-newline': 'off',
        'vue/multiline-html-element-content-newline': 'off',
        'vue/max-attributes-per-line': 'off',
        'vue/attributes-order': 'off',
        'vue/html-self-closing': 'off',
        'vue/html-closing-bracket-newline': 'off',
        'vue/first-attribute-linebreak': 'off',
        'vue/html-quotes': 'off',

        // JavaScript 相关
        'no-console': process.env.NODE_ENV === 'production' ? 'warn' : 'off',
        'no-debugger': process.env.NODE_ENV === 'production' ? 'error' : 'off',
        'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
        'prefer-const': 'warn',
        'no-var': 'error'
    },
    globals: {
        // Vite 全局变量
        defineProps: 'readonly',
        defineEmits: 'readonly',
        defineExpose: 'readonly',
        withDefaults: 'readonly'
    }
}
