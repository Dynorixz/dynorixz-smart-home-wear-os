# Галерея интерфейса

[← Главная](../README.md) · [Установить](installation.md) · [Скачать](https://github.com/Dynorixz/dynorixz-smart-home-wear-os/releases/latest)

Это снимки настоящего интерфейса на тестовом доме, полученные автоматическими проверками. В установленном приложении показываются данные вашего аккаунта. Фотографии иллюстрируют тип прибора; конкретный внешний вид модели может отличаться.

## Главный экран и каталог

<table>
  <tr>
    <td align="center"><img src="screenshots/gallery-home-192.png" width="250" alt="Галерея в тёмной теме"></td>
    <td align="center"><img src="screenshots/gallery-home-light.png" width="250" alt="Галерея в светлой теме"></td>
    <td align="center"><img src="screenshots/device-catalog.png" width="250" alt="Общий каталог устройств"></td>
  </tr>
  <tr>
    <td align="center"><strong>Галерея</strong><br>Фотография, комната, состояние, избранное и отдельное питание</td>
    <td align="center"><strong>Светлая тема</strong><br>Тот же интерфейс с адаптированными цветами</td>
    <td align="center"><strong>Все приборы</strong><br>Каталог, поиск, фильтры и быстрый доступ к управлению</td>
  </tr>
</table>

Касание карточки открывает прибор. Кнопка питания работает отдельно; удерживание карточки меняет избранное. Стиль экрана выбирается в настройках: «Список», «Панель» или «Галерея».

## Свет: от быстрого действия к точной настройке

<table>
  <tr>
    <td align="center"><img src="screenshots/light-overview.png" width="250" alt="Общий экран света"></td>
    <td align="center"><img src="screenshots/light-palette.png" width="250" alt="Палитра с крупными образцами"></td>
    <td align="center"><img src="screenshots/light-white.png" width="250" alt="Выбор белого света"></td>
  </tr>
  <tr>
    <td align="center"><strong>Питание и яркость</strong><br>Большая кнопка, шаги −/+ и прокручиваемые регуляторы</td>
    <td align="center"><strong>Цвет</strong><br>Крупные образцы с подписями и отметкой выбора</td>
    <td align="center"><strong>Белый свет</strong><br>Готовые температуры и точный диапазон прибора</td>
  </tr>
</table>

Для произвольного цвета доступны отдельные регуляторы оттенка и насыщенности. Числовые параметры можно менять безелем после касания шкалы. Отображаются только функции, поддерживаемые конкретной лампой. [Подробнее об управлении светом](light-controls-1.7.0.md).

## Датчики и другие приборы

<table>
  <tr>
    <td align="center"><img src="screenshots/sensor-opening.png" width="250" alt="Датчик открытия"></td>
    <td align="center"><img src="screenshots/sensor-motion.png" width="250" alt="Датчик движения"></td>
    <td align="center"><img src="screenshots/control-thermostat.png" width="250" alt="Управление термостатом"></td>
  </tr>
  <tr>
    <td align="center"><strong>Открытие</strong><br>Крупное состояние вместо отключённой серой кнопки</td>
    <td align="center"><strong>Движение</strong><br>Понятные события, единицы измерения и возраст данных</td>
    <td align="center"><strong>Термостат</strong><br>Шкала и кнопки −/+ с ограничениями устройства</td>
  </tr>
</table>

Приложение также выводит температуру, влажность, заряд и другие свойства, переданные API. Отсутствующие значения помечаются явно. [Описание карточек и регуляторов](device-screens-1.6.0.md).

## Системные плитки Wear OS

<table>
  <tr>
    <td align="center"><img src="screenshots/tile-home.png" width="250" alt="Действия дома"></td>
    <td align="center"><img src="screenshots/tile-lights.png" width="250" alt="Быстрый доступ к свету"></td>
    <td align="center"><img src="screenshots/tile-sensors.png" width="250" alt="Температура и влажность"></td>
  </tr>
  <tr>
    <td align="center"><strong>Умный дом</strong><br>До трёх действий, первая карточка крупнее</td>
    <td align="center"><strong>Свет</strong><br>Лампы, фотографии и питание одним нажатием</td>
    <td align="center"><strong>Датчики</strong><br>Крупное значение и переход к подробностям</td>
  </tr>
</table>

Каждая плитка настраивается независимо через шестерёнку. Можно выбрать до двух датчиков; при одном приборе выводится ещё одно доступное показание. Плитки показывают давность снимка, а не обещание непрерывного обновления. [Как добавить и настроить](tiles-1.8.0.md).

## Действия на циферблате

Источник «Действие дома» добавляется в поддерживаемый слот циферблата через системный редактор. Вид и размер дополнения определяет сам циферблат. [Пошаговая настройка](complications.md).
