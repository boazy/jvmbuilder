import com.example.TestDataClass1Builder;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import java.util.List;
import org.junit.runner.RunWith;

@RunWith(JUnitQuickcheck.class)
public class TestDataClass1Test {

  @Property
  public void name(List<String> names, int counter) {
    new TestDataClass1Builder()
            .name(names)
            .counter(counter)
            .build();
  }
}
