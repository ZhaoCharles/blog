package algorithm;

import java.util.Arrays;

/**
 * @author: rj-wb3
 * @date: 2021/12/13
 * @description: 排序
 */
public class Sort {

    /**
     * 直接插入排序
     *
     * @param numArr
     */
    public static void insertionSort(int[] numArr) {
        if (numArr.length <= 1) {
            return;
        }
        System.out.println("原始数据：" + Arrays.toString(numArr));
        for (int i = 1; i < numArr.length; i++) {
            int nextInsertNum = numArr[i];
            int j = i - 1;
            for (; j >= 0; j--) {
                if (numArr[j] > nextInsertNum) {
                    numArr[j + 1] = numArr[j];
                } else {
                    break;
                }
            }
            numArr[j + 1] = nextInsertNum;
            System.out.println("第" + i + "轮排序后的数组：" + Arrays.toString(numArr));
        }
    }

    public static void main(String[] args) {
        int[] numArr = {9, 3, 5, 1, 10, 23, 12, 4};
        //插入排序
        insertionSort(numArr);
    }
}
