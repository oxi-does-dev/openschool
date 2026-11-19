package ru.sch.diary;

/**
 * Арифметика оценок без Android: её удобно проверять отдельно от UI.
 *
 * «Выйдет» — школьное округление среднего балла: 5 начинается с 4,56,
 * 4 — с 3,56, 3 — с 2,56. Среднее сначала округляется до сотых, чтобы
 * подпись «4,56» и итоговая оценка не расходились. Калькулятор считает,
 * какой средний балл нужен за оставшиеся оценки, чтобы дотянуть до
 * выбранной.
 */
public final class Stats {

    private Stats() {
    }

    /** Нижняя граница среднего балла для итоговой оценки. */
    public static double thresholdFor(int grade) {
        if (grade >= 5) return 4.56;
        if (grade == 4) return 3.56;
        return 2.56;
    }

    /** Итоговая оценка по среднему баллу. */
    public static int gradeOf(double average) {
        // Сравниваем то же число, которое видит пользователь: 4,555
        // показывается как 4,56 и должно давать 5, а 4,554 — ещё 4.
        double shown = Math.round(average * 100.0) / 100.0;
        if (shown >= 4.56) return 5;
        if (shown >= 3.56) return 4;
        if (shown >= 2.56) return 3;
        return 2;
    }

    /**
     * Средний балл за {@code future} будущих оценок, чтобы среднее стало
     * не ниже порога для {@code grade}. Может быть больше 5 — тогда плана
     * не существует.
     */
    public static double requiredAverage(double sum, double weight,
                                         int grade, int future) {
        if (future <= 0) return Double.NaN;
        double need = thresholdFor(grade) * (weight + future) - sum;
        return need / future;
    }

    /**
     * Сколько пятёрок подряд нужно, чтобы дойти до порога. 0 — уже хватает.
     * -1 — недостижимо (порог не ниже 5, чего школьное округление не даёт).
     */
    public static int minMarks(double sum, double weight, int grade) {
        double need = thresholdFor(grade) * weight - sum;
        if (need <= 0) return 0;
        double perFive = 5.0 - thresholdFor(grade);
        if (perFive <= 0) return -1;
        return (int) Math.ceil(need / perFive - 1e-9);
    }

    /** Средний балл после одной оценки {@code mark}. */
    public static double projectedAfter(double sum, double weight, int mark) {
        if (weight <= 0) return mark;
        return (sum + mark) / (weight + 1);
    }

    /** Средний балл после {@code count} одинаковых оценок {@code mark}. */
    public static double projectedAfter(double sum, double weight,
                                        int mark, int count) {
        if (weight + count <= 0) return mark;
        return (sum + mark * count) / (weight + count);
    }
}
