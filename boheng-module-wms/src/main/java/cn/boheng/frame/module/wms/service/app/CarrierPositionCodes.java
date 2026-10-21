package cn.boheng.frame.module.wms.service.app;

import cn.boheng.frame.module.wms.dal.dataobject.container.ContainerTypeDO;

import java.util.ArrayList;
import java.util.List;

/**
 * 载具（CARRIER）孔位命名，与 {@code MaterialInstanceServiceImpl#buildPositionCodes} 一致
 */
final class CarrierPositionCodes {

    private CarrierPositionCodes() {
    }

    static List<String> build(ContainerTypeDO carrier) {
        List<String> codes = new ArrayList<>();
        if (carrier == null) {
            return codes;
        }
        int total = carrier.getWellCount() == null ? 0 : carrier.getWellCount();
        if (total <= 0) {
            return codes;
        }
        if ("SEQ".equalsIgnoreCase(carrier.getPositionNaming())) {
            for (int i = 1; i <= total; i++) {
                codes.add(String.valueOf(i));
            }
            return codes;
        }
        int rows = carrier.getWellRows() == null ? 1 : carrier.getWellRows();
        int cols = carrier.getWellCols() == null ? 1 : carrier.getWellCols();
        if (rows * cols < total) {
            for (int i = 1; i <= total; i++) {
                codes.add(String.valueOf(i));
            }
            return codes;
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 1; c <= cols; c++) {
                codes.add(toExcelColumn(r) + c);
            }
        }
        return codes;
    }

    private static String toExcelColumn(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            n--;
            sb.append((char) ('A' + n % 26));
            n /= 26;
        }
        return sb.reverse().toString();
    }
}
