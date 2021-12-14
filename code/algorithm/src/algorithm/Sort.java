package algorithm;

import java.util.Arrays;

/**
 * @author: rj-wb3
 * @date: 2021/12/14
 * @description: 排序算法
 */
public class Sort {

    /**
     * 插入排序
     *
     * @param numArr
     */
    public static void insertionSort(int[] numArr) {
        if (numArr.length <= 1) {
            return;
        }
        System.out.println("排序前的数组：" + Arrays.toString(numArr));
        for (int i = 1; i < numArr.length; i++) {
            int j = i - 1;
            int curNum = numArr[i];
            for (; j >= 0 && numArr[j] > curNum; j--) {
                numArr[j + 1] = numArr[j];
            }
            numArr[j + 1] = curNum;
            System.out.println("第" + i + "次排序后的数组：" + Arrays.toString(numArr));
        }
    }

    public static void main(String[] args) {
        int[] numArr = {10,5,1,9,12,42,2,1,6,8};
        insertionSort(numArr);
    }
}
