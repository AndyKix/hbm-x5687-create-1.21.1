# Генерация ресурсов в 0.5.0

Параметры взяты из `HbmWorldGen`, `DungeonToolbox` и эффективных значений
`WorldConfig.loadConfig` официального X5687. Y в таблицах — диапазон центров
жил, частота — попытки на чанк, размер — параметр генератора, а не гарантия
числа блоков. Формы/рельеф/расположение на одинаковом seed отличаются от 1.7.10.

JSON расположен в `data/hbm/worldgen` и `data/hbm/neoforge/biome_modifier`.
Параметры можно менять datapack. Ранее созданные чанки не переписываются.

## Особые формации

- Гнейс: четырёхоктавный шум, масштаб 0.01, порог 5; слой около Y=30 с исходной формулой толщины. Гнейсовые руды заменяют только гнейс.
- Гематит/боксит/малахит: Y=6…64; исходные масштабы `(0.04, 0.25)`, `(0.03, 0.15)`, `(0.10, 0.15)` и пороги 230/300/275. Генерируются до обычных руд.
- Асбестовая облицовка пещер: около Y=25, двухоктавный шум, порог 1.75, множитель/предел толщины 20. Пустоты не вырезаются. Семя шума привязано к стабильному ID ресурса вместо числового ID старого блока.
- Глубинные железо/титан/вольфрам: шансы 1/24, 1/32, 1/32, радиус 5, заполнение ядра 0.6. Киноварь/цирконий/бура: шанс 1/16, радиус 5, заполнение 0.8. Центр адаптирован к нижней границе: Y=-64…-62. Заполнение происходит выше самой нижней плиты бедрока, заменяет камень/глубинный сланец/верхние блоки бедрока ядром и оболочкой.
- Глубинный неодим Незера: два шанса 1/16, центры Y=0…2 и 125…127, радиус 7, заполнение 0.6; запись ограничена Y=1…126.
- Глубинная порода и александрит требуют специального инструмента и не захватываются Create. API сохраняет 50 тиков добычи; игровые продвинутые инструменты ещё впереди.
- Колтан: X/Z центра — два последовательных `(int)(new java.util.Random(seed + 5).nextGaussian() * 1500)`. Два прохода пяти квадратов с полурадиусами `750/r`, r=1…5; Y=15…39, размер 4. Равномерное появление по всему миру не включено, как в обычных настройках X5687.
- Австралий: X и Z от -450 до -350, центры Y=15…29, размер 50. Исходный цикл с новым броском `nextInt(4)` в условии каждой итерации сохранён.

Нефть/газ, кислотные пещеры, буровые месторождения, метеориты/вулканизм и
трансформационные руды перечислены как зависимости в `STAGE5_MAPPING.md`.
Плутониевая руда Незера по умолчанию не добавлена.

Для проверки генераторов в отдельном творческом мире доступны
`/place feature hbm:geological_layers` и `/place feature hbm:regional_resources`.
Они меняют подходящие блоки в месте команды, но сохраняют ограничения шума,
координат и исходной породы; это не команды поиска залежей.

## overworld

| ID | Y | Attempts / rarity | Size | Host rock |
|---|---|---|---|---|
| `ore_uranium` | 5..24 | 6 | 5 | `minecraft:stone` |
| `ore_thorium` | 5..29 | 7 | 5 | `minecraft:stone` |
| `ore_titanium` | 5..34 | 8 | 6 | `minecraft:stone` |
| `ore_sulfur` | 5..34 | 5 | 8 | `minecraft:stone` |
| `ore_aluminium` | 5..44 | 7 | 6 | `minecraft:stone` |
| `ore_copper` | 5..49 | 12 | 6 | `minecraft:stone` |
| `ore_fluorite` | 5..49 | 6 | 4 | `minecraft:stone` |
| `ore_niter` | 5..34 | 6 | 6 | `minecraft:stone` |
| `ore_tungsten` | 5..34 | 10 | 8 | `minecraft:stone` |
| `ore_lead` | 5..34 | 6 | 9 | `minecraft:stone` |
| `ore_beryllium` | 5..34 | 6 | 4 | `minecraft:stone` |
| `ore_rare` | 5..24 | 6 | 5 | `minecraft:stone` |
| `ore_lignite` | 35..59 | 2 | 24 | `minecraft:stone` |
| `ore_asbestos` | 16..31 | 2 | 4 | `minecraft:stone` |
| `ore_cinnebar` | 8..23 | 1 | 4 | `minecraft:stone` |
| `ore_cobalt` | 4..11 | 2 | 4 | `minecraft:stone` |
| `cluster_iron` | 15..59 | 4 | 6 | `minecraft:stone` |
| `cluster_titanium` | 15..44 | 2 | 6 | `minecraft:stone` |
| `cluster_aluminium` | 15..49 | 3 | 6 | `minecraft:stone` |
| `cluster_copper` | 15..34 | 4 | 6 | `minecraft:stone` |
| `ore_gneiss_iron` | 30..39 | 25 | 6 | `hbm:stone_gneiss` |
| `ore_gneiss_gold` | 30..39 | 10 | 6 | `hbm:stone_gneiss` |
| `ore_gneiss_uranium` | 30..39 | 18 | 6 | `hbm:stone_gneiss` |
| `ore_gneiss_copper` | 30..39 | 36 | 6 | `hbm:stone_gneiss` |
| `ore_gneiss_asbestos` | 30..39 | 6 | 6 | `hbm:stone_gneiss` |
| `ore_gneiss_lithium` | 30..39 | 6 | 6 | `hbm:stone_gneiss` |
| `ore_gneiss_rare` | 30..39 | 6 | 6 | `hbm:stone_gneiss` |
| `ore_alexandrite` | 10..14 | 1/100 | 3 | `minecraft:stone` |
| `stone_resource_limestone` | 25..54 | 1 | 16 | `minecraft:stone` |

## nether

| ID | Y | Attempts / rarity | Size | Host rock |
|---|---|---|---|---|
| `ore_nether_uranium` | 0..126 | 8 | 6 | `minecraft:netherrack` |
| `ore_nether_tungsten` | 0..126 | 10 | 10 | `minecraft:netherrack` |
| `ore_nether_sulfur` | 0..126 | 26 | 12 | `minecraft:netherrack` |
| `ore_nether_fire` | 0..126 | 24 | 6 | `minecraft:netherrack` |
| `ore_nether_coal` | 16..111 | 8 | 32 | `minecraft:netherrack` |
| `ore_nether_cobalt` | 100..125 | 2 | 6 | `minecraft:netherrack` |

## end

| ID | Y | Attempts / rarity | Size | Host rock |
|---|---|---|---|---|
| `ore_tikite` | 0..126 | 8 | 6 | `minecraft:end_stone` |
