package AppFrontend.Interface.Insights

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.fractal.R
import com.example.fractal.databinding.FragmentInsightsPagerBinding

class InsightsPagerFragment : Fragment() {

    private var _binding: FragmentInsightsPagerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInsightsPagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Setup the ViewPager Adapter
        val pagerAdapter = InsightsPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        // Set a default page (Device Insights) just in case no tab was clicked
        binding.viewPager.setCurrentItem(1, false)
        updateIndicators(1)

        // 2. INCOMING SYNC: Listen for tab clicks
        // If a tab was clicked in MainActivity, this fires IMMEDIATELY here.
        requireActivity().supportFragmentManager.setFragmentResultListener(
            "tab_change",
            viewLifecycleOwner
        ) { _, bundle ->
            val page = bundle.getInt("page")
            binding.viewPager.setCurrentItem(page, false)
            updateIndicators(page)
        }

        // 3. OUTGOING SYNC: Delay registering the callback by one frame to
        // ignore the ViewPager's initial loading jump.
        binding.viewPager.post {
            binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)

                    updateIndicators(position)

                    val result = Bundle().apply { putInt("page", position) }
                    requireActivity().supportFragmentManager.setFragmentResult("pager_swiped", result)
                }
            })
        }

        // 4. Make the diamonds clickable
        binding.indicatorUsage.setOnClickListener { binding.viewPager.setCurrentItem(0, false) }
        binding.indicatorModel.setOnClickListener { binding.viewPager.setCurrentItem(1, false) }
    }

    private fun updateIndicators(position: Int) {
        setIndicatorState(binding.indicatorUsage, position == 0)
        setIndicatorState(binding.indicatorModel, position == 1)
    }

    private fun setIndicatorState(imageView: ImageView, isSelected: Boolean) {
        if (isSelected) {
            imageView.setImageResource(R.drawable.ic_diamond_indicator_selected)
            imageView.alpha = 1.0f
        } else {
            imageView.setImageResource(R.drawable.ic_diamond_indicator_deselected)
            imageView.alpha = 0.5f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}