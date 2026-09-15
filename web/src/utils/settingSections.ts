export interface SettingSectionMeta {
  key: string
  labelKey: string
  icon: string
}

export const SETTING_SECTION_GROUPS: Array<{ labelKey: string; items: SettingSectionMeta[] }> = [
  {
    labelKey: 'nav.groups.general',
    items: [
      { key: 'system', labelKey: 'nav.sections.system', icon: 'pi pi-wrench' },
      { key: 'appearance', labelKey: 'nav.sections.appearance', icon: 'pi pi-palette' },
      { key: 'notify', labelKey: 'nav.sections.notify', icon: 'pi pi-bell' },
    ],
  },
  {
    labelKey: 'nav.groups.security',
    items: [
      { key: 'accounts', labelKey: 'nav.sections.accounts', icon: 'pi pi-user' },
      { key: 'password', labelKey: 'nav.sections.password', icon: 'pi pi-lock' },
      { key: 'passkey', labelKey: 'nav.sections.passkey', icon: 'pi pi-shield' },
    ],
  },
  {
    labelKey: 'nav.groups.integrations',
    items: [{ key: 'mcp', labelKey: 'nav.sections.mcp', icon: 'pi pi-key' }],
  },
  {
    labelKey: 'nav.groups.data',
    items: [{ key: 'import', labelKey: 'nav.sections.import', icon: 'pi pi-download' }],
  },
  {
    labelKey: 'nav.groups.about',
    items: [{ key: 'about', labelKey: 'nav.sections.about', icon: 'pi pi-heart' }],
  },
]

export const SETTING_SECTION_ITEMS = SETTING_SECTION_GROUPS.flatMap((g) => g.items)

export const SETTING_SECTION_KEYS = new Set(SETTING_SECTION_ITEMS.map((i) => i.key))

export const DEFAULT_SETTING_SECTION = 'system'
