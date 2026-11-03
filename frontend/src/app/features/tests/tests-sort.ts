import { TestSummary } from './tests-source';
export const TEST_SORTS = ['name:asc', 'name:desc', 'materialName:asc', 'materialName:desc', 'createdAt:asc', 'createdAt:desc'] as const;
export type TestSort = typeof TEST_SORTS[number];
const collator = new Intl.Collator('ru', { numeric: true, sensitivity: 'variant' });
export function sortTests(items: readonly TestSummary[], sort: TestSort): TestSummary[] {
  const [key, direction] = sort.split(':') as ['name' | 'materialName' | 'createdAt', 'asc' | 'desc'];
  return items.map((item, index) => ({ item, index })).sort((a, b) => {
    const value = key === 'createdAt' ? Date.parse(a.item.createdAt) - Date.parse(b.item.createdAt) : collator.compare(a.item[key], b.item[key]);
    return (Number.isFinite(value) ? value : 0) * (direction === 'asc' ? 1 : -1) || a.index - b.index;
  }).map(entry => entry.item);
}
